(ns akvo-unified-log.test-util
  (:require [clj-http.client :as http]
            [aero.core :as aero]
            [cheshire.core :as json]
            [clojure.test :as clj-test]
            [akvo.commons.config :as config]
            [taoensso.timbre :as log])
  (:import (java.net Socket)))

(def unilog-url "http://localhost:3030")
(def gae-local {:hostname "localhost"
                :port 8888})

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

(defmacro try-assert
  "Waits for the given form to report no failures using 100ms intervals up to the specified time.
  If it is still reporting failures after the specified time, report a failure."
  ([t form] `(try-assert ~t ~form nil))
  ([t form msg]
   `(loop [countdown# ~(* 1000 t)]
      (let [events# (atom [])
            result# (binding [clojure.test/report
                              (fn [ev#] (swap! events# conj ev#))]
                      (clojure.test/try-expr ~msg ~form))]
        (if (and (seq (remove #(= (:type %) :pass) @events#))
              (> countdown# 0))
          (do
            (Thread/sleep 1000)
            (recur (- countdown# 1000)))
          (do
            (doseq [ev# @events#]
              (clojure.test/report ev#))
            result#))))))

(defn wait-for-server [host port]
  (try-for (str "Nobody listening at " host ":" port) 60
    (with-open [_ (Socket. host (int port))]
      true)))

(defn check-servers-up []
  (wait-for-server "localhost" 3030)
  (wait-for-server "postgres" 5432)
  (wait-for-server "localhost" 8888))

(defn fixture [f]
  (check-servers-up)
  (f))