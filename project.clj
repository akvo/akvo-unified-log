(defproject akvo-unified-log "0.1.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.com/akvo/akvo-unified-log"
  :license {:name "GNU Affero General Public License"
            :url "https://www.gnu.org/licenses/agpl"}
  :plugins [[lein-environ "1.0.0"]
            [lein-ancient "0.6.15"]]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.akvo/commons "0.4.6" :exclusions [[org.clojure/tools.reader]]]

                 ;; DB libs
                 [org.clojure/java.jdbc "0.7.8"]
                 [org.postgresql/postgresql "42.2.5"]
                 [ragtime "0.8.0"]
                 [yesql "0.5.3"]

                 ;; GAE SDK
                 [com.google.appengine/appengine-tools-sdk "1.9.71"]
                 [com.google.appengine/appengine-remote-api "1.9.71"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.9.71"]

                 ;; Ring, routing, jetty
                 [ring/ring-core "1.7.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [compojure "1.6.1"]

                 ;; Logging
                 [com.taoensso/timbre "4.10.0"]

                 ;; API
                 [liberator "0.15.2"]

                 ;; ObjectMapper (JSON serialization)
                 [com.fasterxml.jackson.core/jackson-core "2.9.8"]
                 [com.fasterxml.jackson.core/jackson-databind "2.9.8"]

                 ;; Scheduling
                 [overtone/at-at "1.2.0"]

                 [aero "1.1.3"]
                 [clj-http "3.9.1"]

                 ;; Environment variables
                 [environ "1.1.0"]

                 [org.clojure/tools.nrepl "0.2.13"]

                 [iapetos "0.1.8"]
                 [io.prometheus/simpleclient_hotspot "0.6.0"]
                 [io.prometheus/simpleclient_jetty "0.6.0"]]
  :main akvo-unified-log.core
  :uberjar-name "akvo-unilog.jar"
  :test-selectors {:default (fn [m] (not (or (:integration m) (:kubernetes-test m) (:wip m))))
                   :integration (fn [m] (and (:integration m) (not (:wip m))))
                   :kubernetes-test :kubernetes-test}

  :profiles {:dev {:source-paths ["dev"]}
             :uberjar {:aot :all}})
