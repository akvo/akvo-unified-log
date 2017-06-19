(ns akvo-unified-log.endpoints
  (:require [akvo-unified-log.config :as config]
            [akvo-unified-log.db :as db]
            [akvo-unified-log.json :as json]
            [clj-statsd :as statsd]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [liberator.core :refer (defresource)]
            [taoensso.timbre :as log]))

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
        (log/info "FIXME")
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
