(ns akvo-unified-log.config
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [akvo.commons.git :as git]
            [taoensso.timbre :as log])
  (:import [com.google.apphosting.utils.config AppEngineWebXmlReader]))

(def akvo-config-clone-url
  "git@github.com:akvo/akvo-config.git")

(def akvo-flow-server-config-clone-url
  "git@github.com:akvo/akvo-flow-server-config.git")

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
  (log/infof "Unsubscribing %s from event notifications" org-id)
  (async/unsub event-notification-pub org-id event-subscriber-chan)
  (async/close! event-subscriber-chan))

(defn subscribe [org-id
                 event-notification-pub
                 event-notification-chan
                 event-notification-handler]
  (log/infof "Subscribing %s for event notifications" org-id)
  (let [chan (async/chan (async/sliding-buffer 1))]
    (async/sub event-notification-pub org-id chan)
    (event-notification-handler org-id chan event-notification-chan)
    chan))

(defn subscribe-all [config org-ids]
  (reduce (fn [config org-id]
            (assoc-in config
                      [:instances org-id :event-subscriber-chan]
                      (subscribe org-id
                                 (:event-notification-pub config)
                                 (:event-notification-chan config)
                                 (:event-notification-handler config))))
          config
          org-ids))

(defn reload-config [previous-config]
  (let [event-notification-pub (:event-notification-pub previous-config)
        event-notification-chan (:event-notification-chan previous-config)
        event-notification-handler (:event-notification-handler previous-config)
        next-config (read-config (:repos-dir previous-config)
                                 (:config-file-name previous-config))
        previous-org-ids (set (keys (:instances previous-config)))
        next-org-ids (set (keys (:instances next-config)))]

    (doseq [org-id (set/difference previous-org-ids next-org-ids)]
      (unsubscribe event-notification-pub
                   org-id
                   (get-in previous-config
                           [:instances org-id :event-subscriber-chan])))

    (-> next-config
        (assoc :event-notification-pub event-notification-pub
               :event-notification-chan event-notification-chan
               :event-notification-handler event-notification-handler)
        (subscribe-all (set/difference next-org-ids previous-org-ids)))))

(defn init-config [repos-dir config-file-name event-notification-handler]
  (let [config (read-config repos-dir config-file-name)
        event-notification-chan (async/chan (async/sliding-buffer 1000))
        event-notification-pub (async/pub event-notification-chan :org-id)]

    (-> config
        (assoc :event-notification-chan event-notification-chan
               :event-notification-pub event-notification-pub
               :event-notification-handler event-notification-handler)
        (subscribe-all (keys (:instances config))))))
