(ns akvo-unified-log.core-test
  (:require [clojure.test :refer :all]
            [akvo-unified-log.core :refer :all]
            [clojure.core.async :as async]))

(deftest test-put-interval!
  (let [chan (async/chan)]
    (put-interval! chan :foo 3 10)
    (is (= [:foo :foo :foo]
           [(async/<!! chan)
            (async/<!! chan)
            (async/<!! chan)]))
    (async/close! chan)))
