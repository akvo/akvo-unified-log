(ns akvo-unified-log.scheduler
  (:require [overtone.at-at :as at]))

(defonce ^:private thread-pool nil)

(defonce ^:private delays-in-ms nil)

(defn set-thread-pool
  [num-threads]
  (alter-var-root #'thread-pool at/mk-pool {:cpu-count num-threads}))

(defn set-delays
  [delay-in-seconds]
  (alter-var-root #'delays-in-ms (constantly (mapv (partial * 1000) delay-in-seconds))))

(defn scheduled-jobs
  []
  {:pre [(not (nil? thread-pool))]}
  (at/scheduled-jobs thread-pool))

(defn- stop-all-jobs [org-id]
  (doseq [job (filter #(= org-id (get % :desc)) (scheduled-jobs))]
    (at/stop job)))

(defn schedule
  [org-id f]
  {:pre [(not (nil? thread-pool))]}
  (locking delays-in-ms
    (stop-all-jobs org-id)
    (doseq [delay delays-in-ms]
      (at/after delay #(f delay) thread-pool :desc org-id))))