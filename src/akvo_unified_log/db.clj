(ns akvo-unified-log.db
  (:require [akvo-unified-log.json :as json]
            [akvo.commons.gae :as gae]
            [akvo.commons.gae.query :as query]
            [taoensso.timbre :as log]
            [yesql.core :refer (defqueries)]
            [iapetos.core :as prometheus]
            [iapetos.collector.exceptions :as ex]
            [akvo-unified-log.config :as config])
  (:import [org.postgresql.util PSQLException]
           (java.util.concurrent.locks Lock)))

(defqueries "db.sql")

(defmacro metrics
  [fn-name config & body]
  `(let [labels# (merge {:fn ~fn-name, :result "success"} {:tenant (:org-id ~config)
                                                           :pull-delay (:metrics/pull-delay ~config)})
         failure-labels# (assoc labels# :result "failure")]
     (prometheus/with-success-counter (config/metrics-collector :fn/runs-total labels#)
       (prometheus/with-failure-counter (config/metrics-collector :fn/runs-total failure-labels#)
         (ex/with-exceptions (config/metrics-collector :fn/exceptions-total labels#)
           (prometheus/with-duration (config/metrics-collector :fn/duration-seconds labels#)
             ~@body))))))

(defn last-fetch-date [config]
  (metrics "last-fetch-date" config
    (let [ts (first (last-timestamp {} {:connection (:jdbc-spec config)}))]
      (java.util.Date. (long (or (:timestamp ts) 0))))))

(defn insert-events [config events]
  (doseq [event events]
    (try
      (metrics "insert-events" config
        (insert<! {:payload event} {:connection (:jdbc-spec config)}))
      (catch PSQLException e
        (log/error "Tenant:" (:org-id config) ", error:" (.getMessage e)))))
  (count events))

(defn datastore-spec [org-config]
  (assoc (select-keys org-config [:service-account-id :private-key-file])
    :hostname (:hostname org-config (str (:org-id org-config) ".appspot.com"))
    :port (:port org-config 443)))

(defn payload [entity]
  (or (.getProperty entity "payload")
    (.getValue (.getProperty entity "payloadText"))))

(defn query-gae [config query & [opts]]
  (metrics "query-gae" config
    (.asQueryResultList query
      (query/fetch-options
        (merge {:limit 300} opts)))))

(defn- fetch-and-insert-new-events*
  [config]
  (try
    (gae/with-datastore [ds (datastore-spec config)]
      (let [date (last-fetch-date config)
            _ (log/infof "Fetching data since %s for %s from GAE" date (:org-id config))
            query (.prepare ds (query/query {:kind "EventQueue"
                                             :filter (query/> "createdDateTime" date)
                                             :sort-by "createdDateTime"}))
            first-query-result (query-gae config query)]
        (loop [query-result first-query-result]
          (when-not (empty? query-result)
            (let [event-count (count query-result)]
              (->> query-result
                (map payload)
                (map json/jsonb)
                (insert-events config))
              (log/infof "Inserted %s events into event log %s" event-count (:org-id config))
              (let [cursor (.getCursor query-result)
                    next-query-result (query-gae config query {:start-cursor cursor})]
                (recur next-query-result)))))))
    (catch Throwable e
      (log/error e)
      false)))

(defn fetch-and-insert-new-events
  "Fetch EventQueue data from a FLOW instance and insert it into the
  corresponding postgres event log. Returns true if some events were
  inserted and false otherwise."
  [config]
  (let [lock ^Lock (:lock config)]
    (if (.tryLock lock)
      (try
        (fetch-and-insert-new-events* config)
        (finally (.unlock lock)))
      (log/infof "Skip fetching. Another thread already fetching events for %s" (:org-id config)))))