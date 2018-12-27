(ns akvo-unified-log.test-util
  (:require [clj-http.client :as http]
            [aero.core :as aero]
            [cheshire.core :as json]
            [clojure.test :as clj-test]
            [akvo.commons.config :as config])
  (:import (java.net Socket)))

(def unilog-url "http://localhost:3030")
(def gae-local {:hostname "localhost"
                :port     8888})

(defmacro try-for [msg how-long & body]
  `(let [start-time# (System/currentTimeMillis)]
     (loop []
       (let [[status# return#] (try
                                 (let [result# (do ~@body)]
                                   [(if result# ::ok ::fail) result#])
                                 (catch Throwable e# [::error e#]))
             more-time# (> (* ~how-long 1000)
                           (- (System/currentTimeMillis) start-time#))]
         (cond
           (= status# ::ok) return#
           more-time# (do (Thread/sleep 1000) (recur))
           (= status# ::fail) (throw (ex-info (str "Failed: " ~msg) {:last-result return#}))
           (= status# ::error) (throw (RuntimeException. (str "Failed: " ~msg) return#)))))))

(defn wait-for-server [host port]
  (try-for (str "Nobody listening at " host ":" port) 60
           (with-open [_ (Socket. host (int port))]
             true)))

(defn check-servers-up []
  (wait-for-server "localhost" 3030)
  (wait-for-server "unilog-db" 5432)
  (wait-for-server "localhost" 8888))

(defn fixture [f]
  (check-servers-up)
  (f))