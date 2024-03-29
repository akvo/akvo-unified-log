(ns akvo-unified-log.db-test
  (:require
    [akvo-unified-log.config :as config]
    [clojure.test :refer [deftest testing is]]))

(deftest db-uri
  (testing "db-uri pre-google-cloud"
    (is
      (= "jdbc:postgresql://some-host.com:5432/u_akvoflow-uat1?user=%2C%3B&password=%40%40&ssl=true"
        (config/org-db-uri
          {:database-host "some-host.com"
           :database-name "not used here as it depends on the org-id param"
           :database-password "@@"
           :database-user ",;"
           :tenant-database-prefix "u_"}
          "akvoflow-uat1"))))
  (testing "db-uri with google-cloud"
    (is
      (= "jdbc:postgresql://not_needed/u_akvoflow-uat1?user=%2C%3B&password=%40%40&ssl=false&cloudSqlInstance=some&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
        (config/org-db-uri
          {:database-host "should be ignored"
           :database-name "lumen"
           :database-password "@@"
           :database-user ",;"
           :cloud-sql-instance "some"
           :tenant-database-prefix "u_"}
          "akvoflow-uat1")))))
