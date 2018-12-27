(ns akvo-unified-log.db
  (:require [akvo-unified-log.json :as json]
            [akvo.commons.gae :as gae]
            [akvo.commons.gae.query :as query]
            [clj-statsd :as statsd]
            [taoensso.timbre :as log]
            [yesql.core :refer (defqueries)])
  (:import [org.postgresql.util PSQLException]))

(defqueries "db.sql")

(defn event-log-tenant-db-name [{:keys [tenant-database-prefix org-id]}]
  (str tenant-database-prefix org-id))

(defn event-log-spec [org-config]
  {:subprotocol "postgresql"
   :subname (format "//%s:%s/%s"
              (:database-host org-config)
              (:database-port org-config 5432)
              (event-log-tenant-db-name org-config))
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

(defn datastore-spec [org-config]
  (assoc (select-keys org-config [:service-account-id :private-key-file])
    :hostname (:hostname org-config (str (:org-id org-config) ".appspot.com"))
    :port (:port org-config 443)))

(defn payload [entity]
  (or (.getProperty entity "payload")
      (.getValue (.getProperty entity "payloadText"))))

(defn fetch-and-insert-new-events
  "Fetch EventQueue data from a FLOW instance and insert it into the
  corresponding postgres event log. Returns true if some events were
  inserted and false otherwise."
  [config]
  (try
    (statsd/with-timing (format "%s.fetch_and_insert" (:org-id config))
      (gae/with-datastore [ds (datastore-spec config)]
        (let [date (last-fetch-date (event-log-spec config))
              _ (log/debugf "Fetching data since %s for %s from GAE" date (:org-id config))
              query (.prepare ds (query/query {:kind "EventQueue"
                                               :filter (query/> "createdDateTime" date)
                                               :sort-by "createdDateTime"}))
              first-query-result (.asQueryResultList query (query/fetch-options {:limit 300}))]
          (loop [query-result first-query-result]
            (when-not (empty? query-result)
              (let [event-count (count query-result)]
                (->> query-result
                     (map payload)
                     (map json/jsonb)
                     (insert-events (event-log-spec config)))
                (statsd/gauge (format "%s.event_count" (:org-id config)) event-count)
                (log/debugf "Inserted %s events into event log %s" event-count (:org-id config))
                (let [cursor (.getCursor query-result)
                      next-query-result (.asQueryResultList query
                                                            (query/fetch-options {:limit 300
                                                                                  :start-cursor cursor}))]
                  (recur next-query-result))))))))
    (catch Throwable e
      (statsd/increment (format "%s.insert_and_fetch_exception" (:org-id config)))
      (log/error e)
      false)))
