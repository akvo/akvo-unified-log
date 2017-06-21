(ns akvo-unified-log.core
  (:require [akvo-unified-log.config :as config]
            [akvo-unified-log.db :refer :all]
            [akvo-unified-log.endpoints :as endpoints]
            [akvo-unified-log.scheduler :as scheduler]
            [clj-statsd :as statsd]
            [compojure.core :refer (routes GET POST)]
            [compojure.route :refer (not-found)]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer (wrap-json-body)]
            [ring.middleware.params :refer (wrap-params)]
            [taoensso.timbre :as log])
  (:gen-class))

(defn app [config]
  (routes
   (GET "/status" _ (endpoints/status config))
   (POST "/event-notification" _ (endpoints/event-notification config))
   (POST "/reload-config" _ (endpoints/reload-config config))
   (not-found "Not found")))

(defn -main [repos-dir config-file-name]
  (let [config (assoc (config/read-config repos-dir
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
      (scheduler/set-thread-pool (:num-threads config 5))
      (scheduler/set-delay (:fetch-delay config (* 60 5)))
      (log/infof "Unilog started. Listening on %s" port)
      server)))
