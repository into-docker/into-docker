(ns into.build.add-builder-env-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.build.add-builder-env :as add-builder-env]))

(defspec t-add-builder-env (times 20)
  (prop/for-all
    [spec (s/gen ::spec/spec)]
    (let [data (add-builder-env/run {:spec spec})]
      (= #{"INTO_SOURCE_DIR=/tmp/src"
           "INTO_ARTIFACT_DIR=/tmp/artifacts"}
         (set (:builder-env data))))))
