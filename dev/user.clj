(ns user)

(comment
  (require '[environ.core :refer (env)]
           '[akvo-unified-log.pg :as pg]
           '[clojure.core.async :as async])

  (def db-spec {:subprotocol "postgresql"
                :subname "//localhost/flowaglimmerofhope-hrd"
                :user (env :database-user)
                :password (env :database-password)})

  (def pub (pg/publication db-spec))
  (def chan (pg/subscribe pub ["dataPointCreated"
                               "formInstanceCreated"
                               "answerCreated"]))

  (async/thread
    (loop []
      (when-let [event (async/<!! chan)]
        (pprint event)
        (recur)))
    (println "Exiting thread"))

  (pg/close! pub)
  )
