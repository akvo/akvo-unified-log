(ns akvo-unified-log.scheduler
  (:require [overtone.at-at :as at]))

;; Ideally this should be a Component

(defonce ^:private thread-pool nil)

(defonce ^:private delay-in-ms 1)

(defn set-thread-pool
  [num-threads]
  (alter-var-root #'thread-pool at/mk-pool {:cpu-count num-threads}))

(defn set-delay
  [delay-in-seconds]
  (alter-var-root #'delay-in-ms * 1000 delay-in-seconds))

(defn scheduled-jobs
  []
  {:pre [(not (nil? thread-pool))]}
  (at/scheduled-jobs thread-pool))

(defn scheduled?
  [org-id]
  {:pre [(not (nil? thread-pool))]}
  (boolean (some #(= org-id (get % :desc)) (scheduled-jobs))))

(defn schedule
  [org-id f]
  {:pre [(not (nil? thread-pool))]}
  (at/after delay-in-ms f thread-pool :desc org-id))
