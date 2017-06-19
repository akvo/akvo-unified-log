(ns akvo-unified-log.config
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [akvo.commons.git :as git]
            [akvo-unified-log.migrations :as migrations]
            [taoensso.timbre :as log]
            [environ.core :refer [env]])
  (:import [com.google.apphosting.utils.config AppEngineWebXmlReader]))

(def akvo-config-clone-url
  (get env :akvo-config-clone-url "git@github.com:akvo/akvo-config.git"))

(def akvo-flow-server-config-clone-url
  (get env :akvo-flow-server-config-url "git@github.com:akvo/akvo-flow-server-config.git"))

(def valid-log-levels
  #{:trace :debug :info :warn :error :fatal :report})

(defn get-instance-properties
  [path]
  (into {} (-> (str path "/appengine-web.xml")
               io/file
               .getAbsolutePath
               (AppEngineWebXmlReader. "")
               .readAppEngineWebXml
               .getSystemProperties)))

(defn read-remote-api-credentials
  [repos-dir org-id]
  (let [path (format "%s/akvo-flow-server-config/%s" repos-dir org-id)
        instance-properties (get-instance-properties path) ]
    (log/info "Read remote-api credentials for" org-id)
    {:service-account-id (get instance-properties "serviceAccountId")
     :private-key-file (format "%s/%s.p12" path org-id)}))

(defn read-config-file
  [repos-dir config-file-name]
  (log/info "Cloning akvo-config and akvo-flow-server-config to" repos-dir)
  (git/clone-or-pull repos-dir akvo-config-clone-url)
  (git/clone-or-pull repos-dir akvo-flow-server-config-clone-url)
  (-> (format "%s/akvo-config/services/unified-log/%s"
              repos-dir config-file-name)
      slurp
      edn/read-string))

(defn read-config
  [repos-dir config-file-name]
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

(defn update-log-level [previous-log-level next-log-level]
  (if (= previous-log-level next-log-level)
    (log/debugf "Log level %s has not changed" previous-log-level)
    (if (valid-log-levels next-log-level)
      (do (log/infof "Changing log level from %s to %s"
                     previous-log-level next-log-level)
          (log/set-level! next-log-level))
      (log/warnf "Invalid log level %. Keeping log level at %s"
                 next-log-level previous-log-level))))

(defn reload-config [previous-config]
  (let [next-config (read-config (:repos-dir previous-config)
                                 (:config-file-name previous-config))]

    ;; Update log level if it has changed
    (update-log-level (:log-level previous-config :info)
                      (:log-level next-config :info))
    next-config))

(defn init-config [repos-dir config-file-name]
  (let [config (read-config repos-dir config-file-name)]
    config))
