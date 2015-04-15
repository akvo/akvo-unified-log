(ns akvo-unified-log.scheduler
  (:refer-clojure :exclude (delay))
  (:require [taoensso.timbre :refer (debugf infof warnf errorf fatalf error)])
  (:import [java.util.concurrent Executors TimeUnit]))

;; Thread pool used by scheduled tasks.
(defonce scheduler (Executors/newScheduledThreadPool 64))

;; Delay between task runs (in seconds)
(def delay 30)

;; Initial delay before the task is run the first time (in seconds)
(def initial-delay 1)

;; A map of org-id's to running ScheduledTasks
(defonce tasks (atom {}))

(defn cancel-task
  "Cancel task for org-id"
  [org-id]
  {:pre [(string? org-id)]}
  (when-let [task (get @tasks org-id)]
    (.cancel task false)
    (swap! tasks dissoc org-id)))

(defn running? [org-id]
  {:pre [(string? org-id)]}
  (contains? @tasks org-id))

(defn schedule-task [org-id function]
  {:pre [(string? org-id)
         (fn? function)]}
  (when-not (running? org-id)
    (swap! tasks assoc org-id
           (.scheduleWithFixedDelay scheduler
                                    function
                                    initial-delay
                                    delay
                                    TimeUnit/SECONDS))))
