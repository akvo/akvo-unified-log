(ns akvo-unified-log.core
  (:require [akvo.commons.gae :as gae]
            [akvo.commons.gae.query :as query]
            [akvo-unified-log.config :as config]
            [akvo-unified-log.json :as json]
            [akvo-unified-log.endpoints :as endpoints]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer (wrap-params)]
            [ring.middleware.json :refer (wrap-json-body)]
            [compojure.core :refer (routes ANY)]
            [yesql.core :refer (defqueries)]
            [clj-statsd :as statsd]))

(defn event-log-spec [org-config]
  {:subprotocol "postgresql"
   :subname (format "//%s:%s/%s"
                    (:database-host org-config)
                    (:database-port org-config 5432)
                    (:org-id org-config))
   :user (:database-user org-config)
   :password (:database-password org-config)})

(defn datastore-spec [org-config]
  (assoc (select-keys org-config [:service-account-id :private-key-file])
         :hostname (str (:org-id org-config) ".appspot.com")
         :port 443))

(defqueries "db.sql")

(defn last-fetch-date [db-spec]
  (let [ts (first (last-timestamp db-spec))]
    (java.util.Date. (long (or (:timestamp ts) 0)))))

(defn insert-events [db-spec events]
  (doseq [event events]
    (insert<! db-spec event))
  (count events))

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
              event-count (->> (query/result ds
                                             {:kind "EventQueue"
                                              :filter (query/> "createdDateTime" date)
                                              :sort-by "createdDateTime"}
                                             {:limit 300})
                               seq
                               (map payload)
                               (map json/jsonb)
                               (insert-events (event-log-spec config)))]
          (if (pos? event-count)
            (do (statsd/gauge (format "%s.event_count" (:org-id config)) event-count)
                (log/debugf "Inserted %s events into event log %s" event-count (:org-id config))
                true)
            (do (log/debugf "No more events for %s" (:org-id config))
                false)))))
    (catch Throwable e
      (statsd/increment (format "%s.insert_and_fetch_exception" (:org-id config)))
      (log/error e)
      false)))

(defn event-notification-handler [org-id event-chan notification-chan]
  (async/thread
    (loop []
      (when-let [config (async/<!! event-chan)]
        (when (fetch-and-insert-new-events config)
          (async/go
            (async/>! notification-chan config)
            (async/<! (async/timeout (* 10 1000)))
            (async/>! notification-chan config)
            (async/<! (async/timeout (* 2 60 1000)))
            (async/>! notification-chan config)))
        (recur)))
    (async/close! event-chan)
    (log/infof "Exiting event notification thread for %s" org-id)))

(defn app [config]
  (routes
   (ANY "/status" _ (endpoints/status config))
   (ANY "/event-notification" _ (endpoints/event-notification config))
   (ANY "/reload-config" _ (endpoints/reload-config config))))

(defn -main [repos-dir config-file-name]
  (let [config (assoc (config/init-config repos-dir
                                          config-file-name
                                          event-notification-handler)
                      :repos-dir repos-dir
                      :config-file-name config-file-name) ]
    (statsd/setup (:statsd-host config)
                  (:statsd-port config)
                  :prefix (:statsd-prefix config))
    (let [port (Integer. (:port config 3030))
          server (jetty/run-jetty (-> (app (atom config))
                                      wrap-params
                                      wrap-json-body)
                                  {:port port :join? false})]
      (log/infof "Unilog started. Listening on %s" port)
      server)))

(comment
  (def server (-main "/var/tmp/akvo/unified-log" "test.edn"))
  (.stop server))
