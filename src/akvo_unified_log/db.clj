(ns akvo-unified-log.db
  (:require
            [yesql.core :refer (defqueries)]
            [taoensso.timbre :as log])
  (:import [org.postgresql.util PSQLException]))

(defqueries "db.sql")

(defn event-log-spec [org-config]
  {:subprotocol "postgresql"
   :subname (format "//%s:%s/%s"
                    (:database-host org-config)
                    (:database-port org-config 5432)
                    (:org-id org-config))
   :user (:database-user org-config)
   :password (:database-password org-config)})

(defn last-fetch-date [db-spec]
  (let [ts (first (last-timestamp {} {:connection db-spec}))]
    (java.util.Date. (long (or (:timestamp ts) 0)))))

(defn insert-events [db-spec events]
  (doseq [event events]
    (try
      (insert<! {:payload event} {:connection db-spec})
      (catch PSQLException e
        (log/error (.getMessage e)))))
  (count events))
