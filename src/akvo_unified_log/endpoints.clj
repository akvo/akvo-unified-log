(ns akvo-unified-log.endpoints
  (:require [akvo-unified-log.config :as config]
            [akvo-unified-log.db :as db]
            [akvo-unified-log.scheduler :as sch]
            [iapetos.core :as prometheus]
            [liberator.core :refer (defresource)]
            [taoensso.timbre :as log]))

(defresource status [config]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok
  (fn [ctx]
    {:instances (keys (:instances @config))
     :scheduler (map #(select-keys % [:created-at :desc :initial-delay]) (sch/scheduled-jobs))}))

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
      (prometheus/inc config/metrics-collector :event-notifications {:tenant org-id})
      (if (and org-config (not (sch/scheduled? org-id)))
        (select-keys (sch/schedule org-id (fn [] (db/fetch-and-insert-new-events org-config))) [:created-at])
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
