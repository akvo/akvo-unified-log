(ns akvo-unified-log.json
  (:refer-clojure :exclude (set-validator!))
  (:require [clojure.java.io :as io]
            [akvo.commons.config :as config]
            [taoensso.timbre :refer (debugf infof warnf errorf fatalf error)])
  (:import [java.io FileNotFoundException]
           [org.postgresql.util PGobject]
           [com.fasterxml.jackson.databind ObjectMapper]))

(def object-mapper (ObjectMapper.))

(defn jsonb
  "Create a JSONB object"
  [s]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (if (instance? String s)
                 s
                 (.writeValueAsString object-mapper s)))))
