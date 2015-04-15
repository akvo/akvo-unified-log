(ns akvo-unified-log.json
  (:require [clojure.java.io :as io]
            [akvo.commons.config :as config])
  (:import [org.postgresql.util PGobject]
           [com.github.fge.jsonschema.main JsonSchema JsonSchemaFactory]
           [com.fasterxml.jackson.databind JsonNode]
           [com.fasterxml.jackson.databind ObjectMapper]))

(def object-mapper (ObjectMapper.))

(defn json-node [s]
  (.readValue object-mapper s JsonNode))

(defn schema-validator []
  (.getJsonSchema (JsonSchemaFactory/byDefault)
                  (-> @config/settings :event-schema-file io/file .toURI str)))

(defn valid? [json-node]
  ;; TODO don't read the schema file on each validation
  (.validInstance (schema-validator) json-node))

(defn jsonb
  "Create a JSONB object"
  [s]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue s)))
