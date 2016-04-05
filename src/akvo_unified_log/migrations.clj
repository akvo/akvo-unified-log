(ns akvo-unified-log.migrations
  (:require [clojure.java.jdbc :as jdbc]
            [ragtime.repl]
            [ragtime.jdbc]
            [taoensso.timbre :as log]))

(defn database-exists? [db-spec db-name]
  (not (empty? (jdbc/query db-spec
                           ["SELECT 1 from pg_database WHERE datname=?" db-name]))))

(defn create-event-log-db [db-spec org-id]
  (log/infof "Creating database %s" org-id)
  (jdbc/execute! db-spec
                 [(format "CREATE DATABASE \"%s\" WITH TEMPLATE template0 ENCODING 'UTF8'"
                          org-id)]
                 :transaction? false))

(defn create-initial-table [db-spec]
  (jdbc/execute! db-spec
                 ["CREATE TABLE IF NOT EXISTS event_log (
                    id BIGSERIAL PRIMARY KEY,
                    payload JSONB UNIQUE);"])
  (jdbc/execute! db-spec
                 ["ALTER TABLE event_log OWNER TO unilog;"]))

(defn create-initial-timestamp-idx [db-spec]
  (jdbc/execute! db-spec
                 ["CREATE INDEX
                    timestamp_idx ON
                    event_log(cast(payload->'context'->>'timestamp' AS numeric));"]))

(defn master-database-spec [config]
  {:subprotocol "postgresql"
   :subname (format "//%s:%s/unilog"
                    (:database-host config)
                    (:database-port config 5432))
   :user (:database-user config)
   :password (:database-password config)})

(defn database-spec [org-id config]
  (assoc (master-database-spec config)
         :subname (format "//%s:%s/%s"
                          (:database-host config)
                          (:database-port config 5432)
                          org-id)))

(defn migrate [org-id config]
  (let [master-db-spec (master-database-spec config)
        db-spec (database-spec org-id config)]
    (when-not (database-exists? master-db-spec org-id)
      (create-event-log-db master-db-spec org-id)
      (create-initial-table db-spec)
      (create-initial-timestamp-idx db-spec))
    (log/infof "Migrating %s" org-id)
    (ragtime.repl/migrate {:datastore (ragtime.jdbc/sql-database db-spec)
                           :migrations (ragtime.jdbc/load-resources "migrations")})))

(defn migrate-all [org-ids config]
  (doseq [org-id org-ids]
    (migrate org-id config)))
