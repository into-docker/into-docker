(ns into.docker.client-test
  (:require [clojure.test :refer [deftest testing is]]
            [into.docker :as docker]
            [into.docker.client :as client]))

;; ## Fixtures

(def ^:private base-client
  (client/make-from-env))

(def ^:private existing-image
  "busybox:1.33.0")

(def ^:private missing-image
  (str (java.util.UUID/randomUUID) ":v1"))

;; ## Test

(deftest ^:docker t-client
  (let [client (client/start base-client)]
    (testing "pull and inspect"
      (is (= {:image existing-image}
             (docker/pull-image client existing-image)))
      (let [{:keys [RepoTags]} (docker/inspect-image client existing-image)]
        (is (= [existing-image] RepoTags))))
    (testing "pull and inspect unknown"
      (is (thrown-with-msg?
            IllegalStateException
            #"Operation failed:"
            (docker/pull-image client missing-image)))
      (is (nil? (docker/inspect-image client missing-image))))
    (testing "container creation"
      (is (satisfies?
            docker/DockerContainer
            (docker/container client "test-container" existing-image))))))
