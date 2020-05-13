(ns into.build.read-ignore-paths-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.constants :as constants]
            [into.docker.mock :as docker]
            [into.test.generators :refer [gen-file-with-comments]]
            [into.build.spec :as spec]
            [into.build.read-ignore-paths :as step]))

;; ## Helpers

(defn- make-container
  [ignore-file]
  (-> (docker/container)
      (docker/add-file
        (constants/path-for :ignore-file)
        ignore-file)))

;; ## Tests

(defspec t-read-ignore-paths-should-parse-ignore-file (times 50)
  (prop/for-all
    [{:keys [file lines]} (gen-file-with-comments (s/gen ::spec/path))
     spec                 (s/gen ::spec/spec)]
    (= (concat constants/default-ignore-paths lines)
       (-> {:builder-container (make-container file)
            :spec              spec}
           (step/run)
           (:ignore-paths)))))

(defspec t-read-ignore-paths-should-handle-missing-ignore-file (times 5)
  (prop/for-all
    [spec (s/gen ::spec/spec)]
    (= constants/default-ignore-paths
       (-> {:builder-container (docker/container)
            :spec              spec}
           (step/run)
           (:ignore-paths)))))
