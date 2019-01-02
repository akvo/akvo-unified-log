(ns akvo-unified-log.config
  (:require [clojure.java.io :as io]
            [akvo.commons.git :as git]
            [aero.core :as aero]
            [taoensso.timbre :as log]
            [environ.core :refer [env]]
            [iapetos.collector.ring :as ring]
            [iapetos.collector.exceptions :as ex]
            [iapetos.core :as prometheus]
            [iapetos.collector.jvm :as jvm]
            [hikari-cp.core :as hikari])
  (:import [com.google.apphosting.utils.config AppEngineWebXmlReader]
           (com.zaxxer.hikari HikariConfig)
           (java.net URLEncoder)
           (java.util.concurrent.locks ReentrantLock)))

(def valid-log-levels
  #{:trace :debug :info :warn :error :fatal :report})

(def metrics-collector
  (->
    (prometheus/collector-registry)
    (jvm/initialize)
    (prometheus/register
      (prometheus/counter :event-notifications {:labels [:tenant]})
      (prometheus/histogram
        :fn/duration-seconds
        {:description "the time elapsed during execution of the observed function."
         :buckets [0.01, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10]
         :labels [:fn :tenant]})
      (prometheus/counter
        :fn/runs-total
        {:description "the total number of finished runs of the observed function."
         :labels [:fn :result :tenant]})
      (ex/exception-counter
        :fn/exceptions-total
        {:description "the total number and type of exceptions for the observed function."
         :labels [:fn :tenant]}))
    (ring/initialize)))

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
        instance-properties (get-instance-properties path)]
    (log/info "Read remote-api credentials for" org-id)
    {:service-account-id (get instance-properties "serviceAccountId")
     :private-key-file (format "%s/%s.p12" path org-id)}))

(defn clone-flow-server-config
  [{:keys [url local-path]}]
  (log/info "Cloning akvo-flow-server-config to" local-path)
  (git/clone-or-pull local-path url))

(defn event-log-tenant-db-name [tenant-database-prefix org-id]
  (str tenant-database-prefix org-id))

(defn db-uri [{:keys [database-host database-user database-password tenant-database-prefix]} org-id]
  (format "jdbc:postgresql://%s:5432/%s?ssl=true&user=%s&password=%s"
    database-host
    (event-log-tenant-db-name tenant-database-prefix org-id)
    (URLEncoder/encode database-user)
    (URLEncoder/encode database-password)))

(defn create-db-pool [config org-id]
  {:database-name (event-log-tenant-db-name (:tenant-database-prefix config) org-id)
   :jdbc-spec {:datasource
               (hikari/make-datasource {:jdbc-url (db-uri config org-id)
                                        :idle-timeout 300000
                                        :minimum-idle 0
                                        :configure (fn [^HikariConfig config]
                                                     (.setInitializationFailTimeout config -1))
                                        :maximum-pool-size 2})}})

(defn read-config
  [config-file-name]
  (log/info "Reading config" config-file-name)
  (let [config (aero/read-config config-file-name)
        _ (clone-flow-server-config (:flow-server-config-repo config))
        instance-config (reduce
                          (fn [result [org-id instance-specific-config]]
                            (assoc result org-id
                                          (merge {:org-id org-id
                                                  :lock (ReentrantLock.)}
                                            (create-db-pool config org-id)
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
