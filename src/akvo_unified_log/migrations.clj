(ns akvo-unified-log.migrations
  (:require [clojure.java.jdbc :as jdbc]
            [ragtime.repl]
            [ragtime.jdbc]
            [taoensso.timbre :as log]
            [akvo-unified-log.config :as config]))

(defn database-exists? [db-spec db-name]
  (boolean
   (seq (jdbc/query db-spec
                    ["SELECT 1 from pg_database WHERE datname=?" db-name]))))

(defn create-event-log-db [db-spec org-id]
  (log/infof "Creating database %s" org-id)
  (jdbc/execute! db-spec
    [(format "CREATE DATABASE \"%s\" WITH TEMPLATE template0 ENCODING 'UTF8'"
       org-id)]
    {:transaction? false}))

(defn create-initial-table [db-spec]
  (jdbc/execute! db-spec
    ["CREATE TABLE IF NOT EXISTS event_log (
                    id BIGSERIAL PRIMARY KEY,
                    payload JSONB UNIQUE);"]))

(defn create-initial-timestamp-idx [db-spec]
  (jdbc/execute! db-spec
    ["CREATE INDEX
                    timestamp_idx ON
                    event_log(cast(payload->'context'->>'timestamp' AS numeric));"]))

(defn migrate [org-config config]
  (let [master-db-spec (config/db-uri config)
        db-spec (:jdbc-spec org-config)]
    (when-not (database-exists? master-db-spec (:database-name org-config))
      (create-event-log-db master-db-spec (:database-name org-config))
      (create-initial-table db-spec)
      (create-initial-timestamp-idx db-spec))
    (log/infof "Migrating %s" (:org-id org-config))
    (ragtime.repl/migrate {:datastore (ragtime.jdbc/sql-database db-spec)
                           :migrations (ragtime.jdbc/load-resources "migrations")})))
