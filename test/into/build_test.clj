(ns into.build-test
  (:require [clojure.test :refer [deftest are testing]]
            [clojure.tools.logging.test :refer [with-log logged?]]
            [into.docker.client :as client]
            [into.build.flow :as flow]
            [into.build :as build]))

(defn- capture-spec
  [& args]
  (let [data (volatile! nil)]
    (with-redefs [flow/run     #(vreset! data %)
                  client/start identity]
      (build/run args))
    @data))

(deftest t-build-task
  (testing "minimal usage"
    (let [data (capture-spec "-t" "test" "builder")]
      (are [k v] (= v (get-in data [:spec k]))
           :builder-image-name "builder"
           :target-image-name  "test:latest"
           :source-path        "."
           :use-volumes?       true
           :ci-type            nil
           :cache-from         nil
           :cache-to           nil
           :profile            "default")))
  (testing "CLI args"
    (let [data (capture-spec "-t" "test:latest"
                             "-p" "profile-x"
                             "--write-artifacts" "artifacts"
                             "--ci" "github-actions"
                             "--cache" "cache"
                             "--no-volumes"
                             "builder"
                             "./path")]
      (are [k v] (= v (get-in data [:spec k]))
           :builder-image-name "builder"
           :target-image-name  "test:latest"
           :source-path        "./path"
           :use-volumes?       false
           :ci-type            "github-actions"
           :cache-from         "cache"
           :cache-to           "cache"
           :profile            "profile-x")))
  (testing "validation"
    (with-log
      (capture-spec "-t" "" "builder")
      (logged? 'into.utils.task
               :error
               #"\"-t \": Cannot be blank"))))
