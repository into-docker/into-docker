(ns into.build.add-default-labels-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.build.add-default-labels :as add-default-labels]))

(defspec t-add-default-labels (times 20)
  (prop/for-all
    [spec (s/gen ::spec/spec)
     image (s/gen ::spec/target-image)]
    (let [data (add-default-labels/run
                 {:spec         spec
                  :target-image image})
          labels (get-in data [:target-image :labels])]
      (every? #(get labels %)
              ["org.into-docker.version"
               "org.into-docker.revision"
               "org.into-docker.url"
               "org.into-docker.builder-image"
               "org.into-docker.runner-image"
               "maintainer"]))))

(defspec t-add-default-labels-does-nothing-without-target-image (times 20)
  (prop/for-all
    [spec (s/gen ::spec/spec)]
    (let [data {:spec spec}]
      (= data (add-default-labels/run data)))))
