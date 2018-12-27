(ns akvo-unified-log.end-to-end-test
  {:integration true}
  (:require [clojure.test :refer :all]
            [akvo-unified-log.test-util :as test-util]
            [akvo-unified-log.db :as db]
            [akvo.commons.gae :as gae]
            [akvo.commons.gae.query :as query]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.jdbc :as jdbc]
            [aero.core :as aero])
  (:import (com.google.appengine.api.datastore Query DatastoreService Entity)))

(defn insert!
  [^DatastoreService ds kind props]
  (let [entity (Entity. kind)]
    (doseq [k (keys props)]
      (.setProperty entity (name k) (props k)))
    (.put ds entity)))

(defn add-event [timestamp]
  (gae/with-datastore [ds test-util/gae-local]
    (insert! ds "EventQueue"
      {"createdDateTime" (java.util.Date. timestamp)
       "payload" (json/generate-string {:orgId "example"
                                        :entity {:id timestamp}
                                        :context {:timestamp timestamp}})}))
  (test-util/try-for "GAE took too long to sync" 10
    (= 1
      (gae/with-datastore [ds test-util/gae-local]
        (count (iterator-seq (.iterator (query/result ds
                                          {:kind "EventQueue"
                                           :filter (query/= "createdDateTime" (java.util.Date. timestamp))}))))))))

(use-fixtures :once test-util/fixture)

(defn collect-new-events-from-flow []
  (http/post (str test-util/unilog-url "/event-notification")
    {:as :json
     :content-type :json
     :form-params {:orgId "example"}}))

(defn db-spec []
  (assoc (aero/read-config "dev/dev-config.edn") :org-id "example"))

(defn last-log-position []
  (->
    (jdbc/query (db/event-log-spec (db-spec)) "select max(id) from event_log")
    first
    (:max 0)))

(defn events-since [position]
  (->>
    (jdbc/query (db/event-log-spec (db-spec)) ["select * from event_log where id > ?" position])
    (map (fn [x] (update x :payload (fn [content]
                                      (json/parse-string (.getValue content) true)))))
    (map (comp :timestamp :context :payload))
    sort))

(deftest happy-path
  (let [new-event-timestamp (System/currentTimeMillis)
        log-position (last-log-position)]
    (testing "new events are picked up"
      (println "the log position" log-position)
      (println "how many" (jdbc/query (db/event-log-spec (db-spec)) ["select count(*) from event_log"]))
      (add-event new-event-timestamp)
      (add-event (+ 1 new-event-timestamp))
      (collect-new-events-from-flow)
      (test-util/try-assert 10
        (println "how many inside" (jdbc/query (db/event-log-spec (db-spec)) ["select count(*) from event_log"]))
        (is
          (= [new-event-timestamp (+ 1 new-event-timestamp)]
            (events-since log-position)))))

    (let [new-log-position (last-log-position)]
      (testing "old events are ignored"
        (println "the new log position" new-log-position)
        (add-event (dec new-event-timestamp))
        (add-event (+ 2 new-event-timestamp))
        (collect-new-events-from-flow)
        (test-util/try-assert 10
          (is
            (= [(+ 2 new-event-timestamp)]
              (events-since new-log-position))))))))

(comment
  (->> (http/post (str "http://localhost:3030" "/reload-config")))

  (def x {:orgId "akvoflow-uat1",
          :entity {:id 18069139,
                   :questionId 20809116,
                   :formInstanceId 17109124,
                   :formId 14259117,
                   :value "5",
                   :answerType "VALUE",
                   :type "ANSWER"},
          :context {:timestamp 1429824995568, :source {:type "SYSTEM"}},
          :eventType "answerCreated"}))