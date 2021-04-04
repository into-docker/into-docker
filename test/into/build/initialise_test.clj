(ns into.build.initialise-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [clojure.tools.logging.test :refer [with-log]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.build.initialise :as initialise]))

(defspec t-initialise (times 5)
  (prop/for-all
    [spec (s/gen ::spec/spec)]
    (with-log
      (let [data (initialise/run {:spec spec})]
        (pos? (:started-at data))))))
