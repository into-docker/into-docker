(ns into.utils.cache-test
  (:require [into.utils.cache :as cache]
            [into.test.snapshot :refer [is-snap?]]
            [clojure.test :refer [deftest testing is]]))

(deftest t-cache-commands
  (let [target "cache"
        paths ["a.txt" "b.txt"]]
    (testing "cache preparation"
      (is-snap?
       :prepare-cache-command
       (cache/prepare-cache-commands target paths))
      (is (seq (cache/prepare-cache-command target paths))))
    (testing "cache restoration"
      (is-snap?
       :restore-cache-command
       (cache/restore-cache-commands target paths))
      (is (seq (cache/restore-cache-command target paths))))))
