(ns akvo-unified-log.endpoints
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.core.async :as async]
            [akvo-unified-log.config :as config]
            [akvo-unified-log.db :as db]
            [liberator.core :refer (defresource)]
            [taoensso.timbre :as log]
            [clj-statsd :as statsd]))

(defresource status [config]
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :handle-ok
  (fn [ctx]
    (format "<pre>%s</pre>"
            (str/escape
             (with-out-str
               (pp/pprint (keys (:instances @config))))
             {\< "&lt;" \> "&gt;"}))))

(defresource event-notification [config]
  :available-media-types ["application/json"]
  :allowed-methods [:post]
  :new? false
  :processable?
  (fn [ctx]
    (if-let [org-id (get-in ctx [:request :body "orgId"])]
      (assoc ctx :org-id org-id)
      (do (log/warnf "Invalid notification request body: %s" (get-in ctx [:request :body]))
          false)))
  :post!
  (fn [ctx]
    (let [org-id (:org-id ctx)
          org-config (get-in @config [:instances org-id])]
      (log/debugf "Received notification from %s" org-id)
      (statsd/increment (format "%s.event_notification" org-id))
      (if org-config
        (async/put! (:event-notification-chan @config) org-config)
        (log/debugf "Notification from %s ignored" org-id))))
  :handle-exception
  (fn [ctx]
    (log/error (:exception ctx))))

(defresource reload-config [config]
  :available-media-types ["application/json"]
  :allowed-methods [:post]
  :post!
  (fn [ctx]
    (reset! config (config/reload-config @config))))

(defresource event-push [config]
  :available-media-types ["application/json"]
  :allowed-methods [:post]
  :processable?
  (fn [ctx]
    (let [org-id (get-in ctx [:request :body "orgId"])
          events (get-in ctx [:request :body "events"])]
      (if (and org-id events)
        (assoc ctx :org-id org-id :events events)
        (do (log/warnf "Invalid POST request body: %s" (get-in ctx [:request :body]))
          false))))
  :post!
  (fn [ctx]
    (let [org-id (:org-id ctx)
          org-config (get-in @config [:instances org-id])]
      (when org-config
        (let [db-spec (db/event-log-spec org-config)
              result (db/insert-events db-spec (:events ctx))]
          (statsd/increment (format "%s.event_push" org-id result))
          result))))
  :handle-exception
  (fn [ctx]
    (log/error (:exception ctx))))
