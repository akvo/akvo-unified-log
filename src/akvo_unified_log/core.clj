(ns akvo-unified-log.core
  (:require [akvo-unified-log.config :as config]
            [akvo-unified-log.db :refer :all]
            [akvo-unified-log.endpoints :as endpoints]
            [akvo-unified-log.json :as json]
            [akvo.commons.gae :as gae]
            [akvo.commons.gae.query :as query]
            [clj-statsd :as statsd]
            [compojure.core :refer (routes GET POST)]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer (wrap-json-body)]
            [ring.middleware.params :refer (wrap-params)]
            [taoensso.timbre :as log]))


(defn datastore-spec [org-config]
  (assoc (select-keys org-config [:service-account-id :private-key-file])
         :hostname (str (:org-id org-config) ".appspot.com")
         :port 443))

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

(defn app [config]
  (routes
   (GET "/status" _ (endpoints/status config))
   (POST "/event-notification" _ (endpoints/event-notification config))
   (POST "/reload-config" _ (endpoints/reload-config config))))

(defn -main [repos-dir config-file-name]
  (let [config (assoc (config/init-config repos-dir
                                          config-file-name)
                      :repos-dir repos-dir
                      :config-file-name config-file-name)]
    (statsd/setup (:statsd-host config)
                  (:statsd-port config)
                  :prefix (:statsd-prefix config))
    (log/merge-config! {:level (:log-level config :info)
                        :output-fn (partial log/default-output-fn
                                            {:stacktrace-fonts {}})})
    (let [port (Integer. (:port config 3030))
          server (jetty/run-jetty (-> (app (atom config))
                                      wrap-params
                                      wrap-json-body)
                                  {:port port :join? false})]
      (log/infof "Unilog started. Listening on %s" port)
      server)))
