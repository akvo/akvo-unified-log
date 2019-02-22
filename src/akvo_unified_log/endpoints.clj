(ns akvo-unified-log.endpoints
  (:require [akvo-unified-log.config :as config]
            [akvo-unified-log.db :as db]
            [akvo-unified-log.scheduler :as sch]
            [iapetos.core :as prometheus]
            [liberator.core :refer (defresource)]
            [taoensso.timbre :as log]
            [akvo-unified-log.migrations :as migrations]))

(defresource status [config]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok
  (fn [ctx]
    {:instances (keys (:instances @config))
     :scheduler (map #(select-keys % [:created-at :desc :initial-delay]) (sch/scheduled-jobs))}))

(defn create-new-config!
  [config org-id]
  (config/clone-flow-server-config (:flow-server-config-repo @config))
  (swap! config (fn [old-config]
                  (if (get-in old-config [:instances org-id])
                    old-config
                    (assoc-in old-config [:instances org-id]
                      (delay
                        (let [new-config (config/instance-config old-config org-id)]
                          (migrations/migrate new-config old-config)
                          new-config)))))))

(defn get-org-config [config org-id]
  (get-in @config [:instances org-id]))

(defn get-or-create-config! [config org-id]
  (force
    (or
      (get-org-config config org-id)
      (do
        (create-new-config! config org-id)
        (get-org-config config org-id)))))

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
    (let [org-id (:org-id ctx)]
      (prometheus/inc config/metrics-collector :event-notifications {:tenant org-id})
      (select-keys (sch/schedule org-id (fn []
                                          (let [org-config (get-or-create-config! config org-id)]
                                            (db/fetch-and-insert-new-events org-config)))) [:created-at])))
  :handle-exception
  (fn [ctx]
    (log/error (:exception ctx))))

(defresource reload-config [config]
  :available-media-types ["application/json"]
  :allowed-methods [:post]
  :post!
  (fn [ctx]
    (reset! config (config/reload-config @config))))
