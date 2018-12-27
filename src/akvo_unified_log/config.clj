(ns akvo-unified-log.config
  (:require [clojure.java.io :as io]
            [akvo.commons.git :as git]
            [aero.core :as aero]
            [taoensso.timbre :as log]
            [environ.core :refer [env]])
  (:import [com.google.apphosting.utils.config AppEngineWebXmlReader]))

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

(defn clone-flow-server-config
  [{:keys [url local-path]}]
  (log/info "Cloning akvo-flow-server-config to" local-path)
  (git/clone-or-pull local-path url))

(defn read-config
  [config-file-name]
  (log/info "Reading config" config-file-name)
  (let [config (aero/read-config config-file-name)
        _ (clone-flow-server-config (:flow-server-config-repo config))
        common-config (select-keys config [:sentry-dsn
                                           :database-port
                                           :database-password
                                           :database-host
                                           :tenant-database-prefix
                                           :database-user])

        instance-config (reduce
                         (fn [result [org-id instance-specific-config]]
                           (assoc result org-id (merge {:org-id org-id}
                                                       common-config
                                                       (read-remote-api-credentials (-> config :flow-server-config-repo :local-path) org-id)
                                                       instance-specific-config)))
                         {}
                         (:instances config))]

    (assoc config
           :instances instance-config
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
  (let [next-config (read-config (:config-file-name previous-config))]
    (update-log-level (:log-level previous-config :info)
                      (:log-level next-config :info))
    next-config))
