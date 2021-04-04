(ns into.build.finalise-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging.test :refer [with-log logged?]]
            [into.build.finalise :as finalise]
            [into.build.spec :as spec]))

(defspec t-finalise-with-target-image (times 5)
  (prop/for-all
    [target-image (s/gen ::spec/image)]
    (let [data {:target-image target-image
                :started-at   (System/nanoTime)}]
      (with-log
        (and (= data (finalise/run data))
             (logged? 'into.build.finalise
                      :info
                      #"Image \[.+\] has been built successfully\."))))))

(defspec t-finalise-without-target-image (times 5)
  (prop/for-all
    []
    (let [data {:started-at (System/nanoTime)}]
      (with-log
        (and (= data (finalise/run data))
             (logged? 'into.build.finalise
                      :info
                      #"Artifacts have been built successfully\."))))))

(defspec t-finalise-with-error (times 5)
  (prop/for-all
    []
    (let [ex   (Exception. "FAIL")
          data {:started-at (System/nanoTime)
                :error ex}]
      (with-log
        (and (= data (finalise/run data))
             (logged? 'into.log :error #"FAIL")
             (logged? 'into.log :debug ex #"\[Exception\] Stacktrace"))))))

(defspec t-finalise-with-cleanup-error (times 5)
  (prop/for-all
    []
    (let [data {:started-at (System/nanoTime)
                :cleanup-error (Exception. "FAIL")}]
      (finalise/run data)
      (with-log
        (and (= data (finalise/run data))
             (logged? 'into.log
                      :error
                      #"An error occured during cleanup: FAIL"))))))
