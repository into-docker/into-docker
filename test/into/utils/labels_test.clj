(ns into.utils.labels-test
  (:require [into.utils.labels :as labels]
            [clojure.string :as string]
            [clojure.test :refer :all]))

(def spec
  {:target-image  {:full-name "test:latest"
                   :name      "test"
                   :tag       "latest"}
   :builder-image {:full-name "builder:latest"
                   :name      "builder"
                   :tag       "latest"}
   :runner-image  {:full-name "runner:latest"
                   :name      "runner"
                   :tag       "latest"}
   :source-path   "."})

(deftest t-create-labels
  (testing "with revision"
    (let [labels (labels/create-labels spec)]
      (testing "OCI labels"
        (are [k] (not (string/blank? (get labels (str "org.opencontainers.image." k))))
          "revision"
          "created"))
      (testing "basic labels"
        (are [k] (not (string/blank? (get labels (str "org.into-docker." k))))
          "version"
          "revision"
          "url"))
      (testing "image labels"
        (are [k v] (= (get labels (str "org.into-docker." k)) v)
          "builder-image" "builder:latest"
          "runner-image"  "runner:latest"))
      (testing "clear labels"
        (are [k] (= (get labels k) "")
          "maintainer"))))
  (testing "without revision"
    (let [labels (labels/create-labels (assoc spec :source-path "/tmp"))]
      (is (not (contains? labels "org.opencontainers.image.revision"))))))
