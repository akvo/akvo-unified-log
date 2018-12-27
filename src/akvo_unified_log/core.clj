(ns akvo-unified-log.core
  (:require [akvo-unified-log.config :as config]
            [akvo-unified-log.db :refer :all]
            [akvo-unified-log.endpoints :as endpoints]
            [akvo-unified-log.scheduler :as scheduler]
            [akvo-unified-log.migrations :as migrations]
            [clj-statsd :as statsd]
            [clojure.tools.nrepl.server :as nrepl]
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

(defonce system (atom {}))

(defn -main [config-file-name]
  (let [config (config/read-config config-file-name)]
    #_(statsd/setup (:statsd-host config)
                  (:statsd-port config)
                  :prefix (:statsd-prefix config))
    (log/merge-config! {:level (:log-level config :info)
                        :output-fn (partial log/default-output-fn
                                            {:stacktrace-fonts {}})})
    (let [port (Integer. (:port config 3030))
          config-atom (atom config)
          server (jetty/run-jetty (-> (app config-atom)
                                    wrap-params
                                    wrap-json-body)
                   {:port port :join? false})]
      (scheduler/set-thread-pool (:num-threads config 5))
      (scheduler/set-delay (:fetch-delay config (* 60 5)))
      (log/infof "Unilog started. Listening on %s" port)
      (migrations/migrate-all (keys (:instances config)) config)
      (reset! system {:jetty server
                      :nrepl (nrepl/start-server :port 7888 :bind (:nrepl-bind config "localhost"))
                      :config-atom config-atom}))))


(comment
 
  (.stop (-> system deref :jetty))
  (do
    (.stop j)
    (def j (jetty/run-jetty (-> (app (:config-atom (deref system)))
                              wrap-params
                              wrap-json-body)
             {:port 3030 :join? false})))
  )