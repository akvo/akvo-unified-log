(ns akvo-unified-log.config
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [akvo.commons.git :as git]
            [akvo-unified-log.migrations :as migrations]
            [taoensso.timbre :as log])
  (:import [com.google.apphosting.utils.config AppEngineWebXmlReader]))

(def akvo-config-clone-url
  (or (System/getenv "AKVO_CONFIG_CLONE_URL")
      "git@github.com:akvo/akvo-config.git"))

(def akvo-flow-server-config-clone-url
  (or (System/getenv "AKVO_FLOW_SERVER_CONFIG_CLONE_URL")
      "git@github.com:akvo/akvo-flow-server-config.git"))

(defn read-remote-api-credentials [repos-dir org-id]
  (let [path (format "%s/akvo-flow-server-config/%s" repos-dir org-id)
        appengine-web (-> (str path "/appengine-web.xml")
                          io/file
                          .getAbsolutePath
                          (AppEngineWebXmlReader. "")
                          .readAppEngineWebXml
                          .getSystemProperties)]
    (log/info "Read remote-api credentials for" org-id)
    {:service-account-id (get appengine-web "serviceAccountId")
     :private-key-file (format "%s/%s.p12" path org-id)}))

(defn read-config-file [repos-dir config-file-name]
  (log/info "Cloning akvo-config and akvo-flow-server-config to" repos-dir)
  (git/clone-or-pull repos-dir akvo-config-clone-url)
  (git/clone-or-pull repos-dir akvo-flow-server-config-clone-url)
  (-> (format "%s/akvo-config/services/unified-log/%s"
              repos-dir config-file-name)
      slurp
      edn/read-string))

(defn read-config [repos-dir config-file-name]
  (log/info "Reading config" config-file-name)
  (let [config (read-config-file repos-dir config-file-name)
        common-config (select-keys config [:sentry-dsn
                                           :database-port
                                           :database-password
                                           :database-host
                                           :database-user])

        instance-config (reduce
                         (fn [result [org-id instance-specific-config]]
                           (assoc result org-id (merge {:org-id org-id}
                                                       common-config
                                                       (read-remote-api-credentials repos-dir org-id)
                                                       instance-specific-config)))
                         {}
                         (:instances config))]

    (assoc config
           :instances instance-config
           :repos-dir repos-dir
           :config-file-name config-file-name)))

(defn unsubscribe [event-notification-pub org-id event-subscriber-chan]
  (log/infof "Unsubscribing %s from event notifications" org-id))

(defn subscribe [org-id
                 event-notification-pub
                 event-notification-chan
                 event-notification-handler]
  (log/infof "Subscribing %s for event notifications" org-id))

(defn subscribe-all [config org-ids]
  (migrations/migrate-all org-ids config)
  (reduce (fn [config org-id]
            (assoc-in config
                      [:instances org-id :event-subscriber-chan]
                      (subscribe org-id
                                 (:event-notification-pub config)
                                 (:event-notification-chan config)
                                 (:event-notification-handler config))))
          config
          org-ids))

(def valid-levels
  #{:trace :debug :info :warn :error :fatal :report})

(defn update-log-level [previous-log-level next-log-level]
  (if (= previous-log-level next-log-level)
    (log/debugf "Log level %s has not changed" previous-log-level)
    (if (valid-levels next-log-level)
      (do (log/infof "Changing log level from %s to %s"
                     previous-log-level next-log-level)
          (log/set-level! next-log-level))
      (log/warnf "Invalid log level %. Keeping log level at %s"
                 next-log-level previous-log-level))))

(defn reload-config [previous-config]
  (let [event-notification-pub (:event-notification-pub previous-config)
        event-notification-chan (:event-notification-chan previous-config)
        event-notification-handler (:event-notification-handler previous-config)
        next-config (read-config (:repos-dir previous-config)
                                 (:config-file-name previous-config))
        previous-org-ids (set (keys (:instances previous-config)))
        next-org-ids (set (keys (:instances next-config)))]

    ;; Unsubscribe removed instances
    (doseq [org-id (set/difference previous-org-ids next-org-ids)]
      (unsubscribe event-notification-pub
                   org-id
                   (get-in previous-config
                           [:instances org-id :event-subscriber-chan])))

    ;; Update log level if it has changed
    (update-log-level (:log-level previous-config :info)
                      (:log-level next-config :info))


    (-> next-config
        (assoc :event-notification-pub event-notification-pub
               :event-notification-chan event-notification-chan
               :event-notification-handler event-notification-handler)
        ;; Subscribe added instances
        (subscribe-all (set/difference next-org-ids previous-org-ids)))))

(defn init-config [repos-dir config-file-name event-notification-handler]
  (let [config (read-config repos-dir config-file-name)]
    config))
