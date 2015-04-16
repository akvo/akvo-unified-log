(ns akvo-unified-log.json
  (:refer-clojure :exclude (set-validator!))
  (:require [clojure.java.io :as io]
            [akvo.commons.config :as config])
  (:import [java.io FileNotFoundException]
           [org.postgresql.util PGobject]
           [com.github.fge.jsonschema.main JsonSchema JsonSchemaFactory]
           [com.fasterxml.jackson.databind JsonNode]
           [com.fasterxml.jackson.databind ObjectMapper]))

(def object-mapper (ObjectMapper.))

(def schema-validator nil)

(defn json-node [s]
  (.readValue object-mapper s JsonNode))

(defn valid? [json-node]
  (assert schema-validator "No schema-validator set")
  (.validInstance schema-validator json-node))

(defn jsonb
  "Create a JSONB object"
  [s]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue s)))

(defn make-schema-validator [_ root-schema-file]
  {:pre [(string? root-schema-file)]
   :post [(instance? JsonSchema %)]}
  (let [file (io/file root-schema-file)
        file-absolute-path (-> file .toURI str)]
    (when-not (.exists file)
      (throw (FileNotFoundException. file-absolute-path)))
    (.getJsonSchema (JsonSchemaFactory/byDefault)
                    file-absolute-path)))

(defn set-validator! [schema-root-file]
  (alter-var-root #'schema-validator make-schema-validator schema-root-file))
