(ns into.utils.labels-test
  (:require [into.utils.labels :as labels]
            [into spec]
            [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]))

(defspec t-default-labels (times 20)
  (prop/for-all
   [spec (s/gen :into/spec)]
    (let [labels (labels/create-labels {:spec spec})]
      (and (every? #(not (string/blank? (labels %)))
                   ["org.into-docker.revision"
                    "org.into-docker.url"
                    "org.into-docker.version"])
           (= (labels "org.into-docker.builder-image")
              (get-in spec [:builder-image :full-name]))
           (= (labels "org.into-docker.runner-image")
              (get-in spec [:runner-image :full-name]))
           (= (labels "maintainer") "")))))

(defspec t-oci-labels (times 20)
  (prop/for-all
   [spec (s/gen :into/spec)
    ci   (s/gen :into/ci)]
    (let [data   {:spec spec, :ci ci}
          labels (labels/create-labels data)]
      (and (= (labels "org.opencontainers.image.revision")
              (:ci-revision ci))
           (= (labels "org.opencontainers.image.version")
              (:ci-version ci))
           (= (labels "org.opencontainers.image.source")
              (:ci-source ci))
           (labels "org.opencontainers.image.created")))))
