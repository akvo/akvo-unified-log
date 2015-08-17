(ns akvo-unified-log.core
  (:require [akvo.commons.config :as config]
            [akvo.commons.gae :as commons-gae]
            [akvo-unified-log.gae :as gae]
            [akvo-unified-log.json :as json]
            [akvo-unified-log.scheduler :as scheduler]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :refer (pprint)]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :refer (debugf infof warnf errorf fatalf error)]
            [liberator.core :refer (resource defresource)]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer (wrap-params)]
            [ring.middleware.json :refer (wrap-json-body)]
            [compojure.core :refer (defroutes ANY)]
            [yesql.core :refer (defqueries)]
            [cheshire.core :refer (generate-string)]
            [environ.core :refer (env)]
            [clj-time.core :as t])
  (:import [java.util.concurrent Executors TimeUnit]
           [org.postgresql.util PGobject]
           [com.github.fge.jsonschema.main JsonSchema JsonSchemaFactory]
           [com.fasterxml.jackson.databind JsonNode]
           [com.fasterxml.jackson.databind ObjectMapper]))

(defonce scheduler (Executors/newScheduledThreadPool 64))

;; A map of registered instances. Maps an org-id (which is also the db
;; name of that instance) to a map of information about the state of
;; the instance. e.g.
;; {\"flowaglimmerofhope-hrd\" {:org-id \"flowaglimmerofhope-hrd\"
;;                              :url \"flowaglimmerofhope.appspot.com\"
;;                              :last-notification #<DateTime ...>
;;                              :last-insert-datetime #<DateTime ...>
;;                              :last-event-count 12
;;                              :total-event-count 5321
;;                              :started #<DateTime ...>
;;                              :status :idle}}
(defonce instances (atom {}))

(defn db-spec [org-id]
  (let [settings @config/settings]
    {:subprotocol "postgresql"
     :subname (format "//%s:%s/%s"
                      (:database-host settings)
                      (:database-port settings 5432)
                      org-id)
     :user (:database-user settings)
     :password (:database-password settings)}))

(defqueries "db.sql")

(defn datastore-spec [instance-url]
  (let [settings @config/settings]
    (if (:local-datastore? settings)
      {} ;; Empty spec uses local datastore
      {:server instance-url
       :port 443
       :email (:remote-api-email settings)
       :password (:remote-api-password settings)})))

;; TODO Cache installer so we don't need to re-autheniticate each time
;;      See RemoteApiOptions javadoc
;; TODO Use akvo.commons.gae
(defn fetch-data [instance-url since]
  (commons-gae/with-datastore [ds (datastore-spec instance-url)]
    (->> (gae/fetch-data-iterator ds since 1000)
         iterator-seq
         (map #(or (.getProperty % "payload")
                   (.getValue (.getProperty % "payloadText"))))
         ;; We need two representations, one for validation/sorting and one for postgres
         (map (fn [s]
                {:string s
                 :jsonb (json/jsonb s)
                 :json-node (json/json-node s)}))
         ;; TODO We fetch sorted by createdDateTime so this isn't necessary?
         (sort-by #(-> % :json-node (.get "context") (.get "timestamp") .longValue))
         vec)))

(defn last-fetch-date [db-spec]
  (let [ts (first (last-timestamp db-spec))]
    (java.util.Date. (long (or (:timestamp ts) 0)))))

(defn validate-events [events]
  (doseq [event events]
    (when-not (json/valid? event)
      (warnf "Event %s does not follow schema" event))))

;; TODO bulk insert
(defn insert-events [db-spec events]
  (doseq [event events]
    (insert<! db-spec event)))

;; TODO Don't insert on validation failure?
(defn fetch-and-insert-new-events
  "Fetch and insert new events for org-id. Return the number of events inserted"
  [db-spec org-id url]
  (let [events (fetch-data url (last-fetch-date db-spec))]
    (validate-events (map :json-node events))
    (insert-events db-spec (map :jsonb events))
    (count events)))

(defn fetch-and-insert-task [org-id]
  (let [db-spec (db-spec org-id)]
    (fn []
      (try
        (if-let [instance-data (get @instances org-id)]
          ;; Figure out if we want to continue data fetching or cancel
          ;; the task and wait for notification from the GAE instance
          (let [{:keys [url last-notification last-event-count]} instance-data
                now (t/now)
                five-minutes-ago (t/minus now (t/minutes 5))]
            (if (or (pos? (or last-event-count 0))
                    (t/within? (t/interval five-minutes-ago now)
                               last-notification))
              (let [event-count (fetch-and-insert-new-events db-spec org-id url)]
                (debugf "Inserted %s events into %s" event-count org-id)
                (swap! instances assoc-in [org-id :last-insert-date] now)
                (swap! instances assoc-in [org-id :last-event-count] event-count)
                (swap! instances update-in [org-id :total-event-count] (fnil + 0) event-count))
              (do
                (infof "No new events for %s and no notifications from GAE. Halting data fetching for now" org-id)
                (scheduler/cancel-task org-id))))
          (do
            (warnf "No instance data for %s. Cancelling task" org-id)
            (scheduler/cancel-task org-id)))
        (catch Exception e
          (warnf "Unexpected exception during fetch/insert: %s" (.getMessage e))
          (error e)
          (warnf "Cancelling task for %s" org-id)
          (swap! instances dissoc org-id)
          (scheduler/cancel-task org-id))))))

(defn json-content-type? [ctx]
  (let [content-type (get-in ctx [:request :headers "content-type"])]
    (if (= content-type "application/json")
      true
      (do (warnf "Invalid content type: %s" content-type)
          false))))

(defroutes app
  (ANY "/status" []
       (resource
        :available-media-types ["text/html"]
        :allowed-methods [:get]
        :handle-ok (fn [ctx]
                     (format "<pre>%s</pre>"
                             (str/escape
                              (with-out-str
                                (->> @instances
                                     vals
                                     (sort-by :last-insert-date)
                                     reverse
                                     pprint))
                              {\< "&lt;" \> "&gt;"})))))
  (ANY "/event-notification" []
       (resource
        :available-media-types ["application/json"]
        :allowed-methods [:post]
        :known-content-type? json-content-type?
        :processable? (fn [ctx]
                        (if-let [org-id (get-in ctx [:request :body "orgId"])]
                          (if-let [org-data (get @config/configs org-id)]
                            (assoc ctx
                                   :org-id org-id
                                   :org-data org-data)
                            (do (warnf "No data found for orgId: %s. Request body: %s"
                                       org-id
                                       (get-in ctx [:request :body]))
                                false))
                          (do (warnf "Invalid notification request body: %s"
                                     (get-in ctx [:request :body]))
                              false)))
        :post! (fn [ctx]
                 (let [org-id (:org-id ctx)
                       url (get-in ctx [:org-data :domain])]
                   (debugf "Received notification from %s" org-id)
                   (swap! instances update-in [org-id] merge {:org-id org-id
                                                              :url url
                                                              :last-notification (t/now)})
                   (if (scheduler/running? org-id)
                     (debugf "ScheduledFuture is already running for %s" org-id)
                     (do
                       (infof "Scheduling data fetching for %s" org-id)
                       (scheduler/schedule-task org-id (fetch-and-insert-task org-id))))))
        :new? false))

  (ANY "/events/:org-id" [org-id]
       (resource
        :available-media-types ["application/json"]
        :allowed-methods [:post]
        :known-content-type? json-content-type?
        :post! (fn [ctx]
                 (jdbc/with-db-connection [db-conn (db-spec org-id)]
                   (doseq [event-string (map generate-string (-> ctx :request :body))]
                     (let [jsonb (json/jsonb event-string)]
                       ;; TODO validate?
                       (insert-events db-conn [jsonb]))))))))

(defn -main [settings-file]
  (config/set-settings! settings-file)
  (let [settings @config/settings]
    (config/set-config! (:config-folder settings))
    (json/set-validator! (:event-schema-file settings))
    (let [port (Integer. (:port settings 3030))]
      (jetty/run-jetty (-> #'app
                           wrap-params
                           wrap-json-body)
                       {:port port :join? false}))))
