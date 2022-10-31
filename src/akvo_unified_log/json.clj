(ns akvo-unified-log.json
  (:import
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
                 (.writeValueAsString ^ObjectMapper object-mapper ^Object s)))))
