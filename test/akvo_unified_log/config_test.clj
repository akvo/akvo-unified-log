(ns akvo-unified-log.config-test
  (:require [clojure.test :refer :all]
            [akvo-unified-log.config :refer :all]))

(defn read-remote-api-credentials-mock [repos-dir org-id]
   (let [path (format "%s/akvo-flow-server-config/%s" repos-dir org-id)]
     {:service-account-id (str "service-account-credentials-for-" org-id)
      :private-key-file (format "%s/%s.p12" path org-id)}))

(use-fixtures :each
  (fn [f]
    (with-redefs [read-remote-api-credentials read-remote-api-credentials-mock]
      (f))))

(def repos-dir "/var/tmp/akvo/unified-log")
(def config-file-name "test.edn")
(def event-notification-handler (fn [org-id new-event-chan publish-event-chan]))
(def base-config
  {;; port to run the service
   :port 3030

   ;; postgreSQL host, port, username and password
   :database-host "db-host"
   :database-port "5432"
   :database-user "unilog"
   :database-password "db-password"

   ;; sentry dsn
   :sentry-dsn "the-sentry-dsn"

   ;; statsd config
   :statsd-host "127.0.0.1"
   :statsd-port "8125"
   :statsd-prefix "unilog.test."})

(defn read-config-file-mock-fn [instances]
  (fn [repos-dir config-file-name]
    (assoc base-config :instances instances)))

(defn valid-instance? [config instance]
  (let [instance-config (get-in config [:instances instance])]
    (every? #(contains? instance-config %)
            [:sentry-dsn
             :database-port
             :database-password
             :database-host
             :database-user
             :org-id])))

(defn missing-instance? [config instance]
  (nil? (get-in config [:instances instance])))

(deftest config-test
  (testing "initialize config without any instances"
    (with-redefs [read-config-file (read-config-file-mock-fn {})]
      (let [config (init-config repos-dir config-file-name event-notification-handler)]
        (is (map? config))
        (is (contains? config :instances))
        (is (empty? (:instances config))))))

  (testing "initialize config with instances"
    (with-redefs [read-config-file (read-config-file-mock-fn {"instance-1" nil
                                                              "instance-2" nil})]
      (let [config (init-config repos-dir config-file-name event-notification-handler)]
        (is (valid-instance? config "instance-1"))
        (is (valid-instance? config "instance-2")))))

  (testing "reload-config"
    (let [config (atom nil)]
      (with-redefs [read-config-file (read-config-file-mock-fn {})]
        (reset! config (init-config repos-dir config-file-name event-notification-handler))
        (with-redefs [read-config-file (read-config-file-mock-fn {"instance-1" nil
                                                                  "instance-2" nil})]
          (reset! config (reload-config @config))
          (is (valid-instance? @config "instance-1"))
          (is (valid-instance? @config "instance-2"))
          (with-redefs [read-config-file (read-config-file-mock-fn {"instance-1" nil})]
            (reset! config (reload-config @config))
            (is (valid-instance? @config "instance-1"))
            (is (missing-instance? @config "instance-2"))))))))
