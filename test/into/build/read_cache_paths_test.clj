(ns into.build.read-cache-paths-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is]]
            [into.constants :as constants]
            [into.test.docker :as docker]
            [into.test.generators :refer [gen-file-with-comments]]
            [into.build.spec :as spec]
            [into.build.read-cache-paths :as step]))

;; ## Helpers

(defn- make-container
  [cache-file]
  (-> (docker/container)
      (docker/add-file
        (constants/path-for :cache-file)
        cache-file)))

(defn with-source-prefix
  [paths]
  (set
    (map
      #(str (constants/path-for :source-directory) "/" %)
      paths)))

;; ## Tests

(defspec t-read-cache-paths-should-parse-cache-file (times 50)
  (prop/for-all
    [{:keys [file lines]} (gen-file-with-comments (s/gen ::spec/path))]
    (= (with-source-prefix lines)
       (-> {:builder-container (make-container file)}
           (step/run)
           (:cache-paths)
           (set)))))

(deftest t-read-cache-paths-should-handle-missing-cache-file
  (is (= #{}
         (-> {:builder-container (docker/container)}
             (step/run)
             (:cache-paths)
             (set)))))
