(ns akvo-unified-log.scheduler
  (:require [overtone.at-at :as at]))

;; Ideally this should be a Component

(defonce ^:private thread-pool nil)

(defn setup-thread-pool
  [num-threads]
  (alter-var-root #'thread-pool at/mk-pool {:cpu-count num-threads}))

(defn scheduled-jobs
  []
  (at/scheduled-jobs thread-pool))

(defn scheduled?
  [org-id]
  (boolean (some #(= org-id (get % :desc)) (scheduled-jobs))))

(defn schedule-fetch
  [org-id delay-in-seconds]
  (at/after (* 1000 delay-in-seconds) #(println "Here") thread-pool :desc org-id))
