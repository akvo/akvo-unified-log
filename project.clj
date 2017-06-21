(defproject akvo-unified-log "0.1.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.com/akvo/akvo-unified-log"
  :license {:name "GNU Affero General Public License"
            :url "https://www.gnu.org/licenses/agpl"}
  :plugins [[lein-environ "1.0.0"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.akvo/commons "0.4.2" :exclusions [[org.clojure/tools.reader]]]

                 ;; DB libs, Note: Unilog env still uses JDK7
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.postgresql/postgresql "9.4.1212.jre7"]
                 [ragtime "0.7.1"]
                 [yesql "0.5.3"]

                 ;; GAE SDK
                 [com.google.appengine/appengine-tools-sdk "1.9.53"]
                 [com.google.appengine/appengine-remote-api "1.9.53"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.9.53"]

                 ;; Ring, routing, jetty
                 [ring/ring-core "1.6.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-jetty-adapter "1.6.1"]
                 [compojure "1.6.0"]

                 ;; Logging
                 [com.taoensso/timbre "4.10.0"]

                 ;; API
                 [liberator "0.14.1"]

                 ;; statsd -> graphite
                 [clj-statsd "0.4.0"]

                 ;; ObjectMapper (JSON serialization)
                 [com.fasterxml.jackson.core/jackson-core "2.8.6"]
                 [com.fasterxml.jackson.core/jackson-databind "2.8.6"]

                 ;; Scheduling
                 [overtone/at-at "1.2.0"]

                 ;; Environment variables
                 [environ "1.1.0"]]
  :main akvo-unified-log.core
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ;; ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.nrepl "0.2.12"]]}
             :uberjar {:aot :all}})
