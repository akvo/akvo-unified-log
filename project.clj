(defproject akvo-unified-log "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.com/akvo/akvo-unified-log"
  :license {:name "GNU Affero General Public License"
            :url "https://www.gnu.org/licenses/agpl"}
  :plugins [[lein-environ "1.0.0"]]
  :pedantic? :abort
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.akvo/commons "0.4.2" :exclusions [[org.clojure/tools.reader]]]
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [com.github.fge/json-schema-validator "2.2.6" :exclusions [[joda-time]]]
                 [com.google.appengine/appengine-tools-sdk "1.9.30"]
                 [com.google.appengine/appengine-remote-api "1.9.30"]
                 [com.google.appengine/appengine-api-1.0-sdk "1.9.30"]
                 [ring/ring-core "1.4.0" :exclusions [[org.clojure/tools.reader]]]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [com.taoensso/timbre "4.1.4" :exclusions [[org.clojure/tools.reader]]]
                 [clj-http "2.0.0"]
                 [liberator "0.14.0"]
                 [compojure "1.4.0"]
                 [yesql "0.5.1"]
                 [cheshire "5.5.0"]
                 [environ "1.0.1"]
                 [clj-time "0.11.0"]
                 [clj-statsd "0.3.11"]]
  ;; TODO figure out :profiles {:dev {:source-paths ["dev"]}}
  :source-paths ["dev" "src"]
  :main akvo-unified-log.core
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ;; ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
