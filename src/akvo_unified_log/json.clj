(ns akvo-unified-log.json
  (:refer-clojure :exclude (set-validator!))
  (:require [clojure.java.io :as io]
            [taoensso.timbre :refer (debugf infof warnf errorf fatalf error)]
            [com.stuartsierra.component :as component])
  (:import [java.io FileNotFoundException]
           [org.postgresql.util PGobject]
           [com.github.fge.jsonschema.main JsonSchemaFactory]
           [com.fasterxml.jackson.databind JsonNode]
           [com.fasterxml.jackson.databind ObjectMapper]))

(def object-mapper (ObjectMapper.))

(def schema-validator nil)

(defn json-node [s]
  (.readValue object-mapper s JsonNode))

(defn valid?
  [validator json-node]
  (.validInstance validator json-node))

(defn jsonb
  "Create a JSONB object"
  [s]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue s)))

(defn make-schema-validator [_ root-schema-file]
  (let [file (io/file root-schema-file)
        file-absolute-path (-> file .toURI str)]
    (when-not (.exists file)
      (throw (FileNotFoundException. file-absolute-path)))
    (.getJsonSchema (JsonSchemaFactory/byDefault)
                    file-absolute-path)))

(defn set-validator! [schema-root-file]
  (try (alter-var-root #'schema-validator make-schema-validator schema-root-file)
       (catch Exception e
         (warnf e "Could not initialize JSON schema validation. Event validation disabled."))))


(defrecord JSONValidator [schema-file validator]
  component/Lifecycle
  (start [this]
    (if validator
      this
      (assoc this :validator (.getJsonSchema (JsonSchemaFactory/byDefault) schema-file))))
  (stop [this]
    (if validator
      (assoc this :validator nil)
      this)))

(defn new-validator [schema-file]
  (map->JSONValidator {:schema-file schema-file}))