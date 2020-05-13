(ns into.utils.collect-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [into.test.generators :refer [gen-unique-paths]]
            [into.test.files :as files]
            [into.utils.collect :as collect]))

;; ## Helper

(defn- collect-from
  [^java.io.File target & [opts]]
  (set
   (collect/collect-by-patterns
    (.getCanonicalPath target)
    opts)))

;; ## Tests

(defspec t-collect-by-patterns-should-collect-all-by-default (times 10)
  (prop/for-all
   [paths (gen-unique-paths)]
    (files/with-temp-dir [target paths]
      (= (set paths) (collect-from target)))))

(defspec t-collect-by-patterns-should-prefer-exclusions (times 10)
  (prop/for-all
   [paths (gen-unique-paths)]
    (files/with-temp-dir [target paths]
      (empty? (collect-from target {:exclude ["**"]})))))

(defspec t-collect-by-patterns (times 20)
  (prop/for-all
    [paths (gen-unique-paths 3)]
    (files/with-temp-dir [target paths]
      (let [[a b c] (seq paths)]
        (and (= #{a b} (collect-from target {:include [a b]}))
             (= #{b c} (collect-from target {:exclude [a]}))
             (= #{a}   (collect-from target {:include [a b], :exclude [b]})))))))
