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

(deftest ^:docker t-client-platforms
  (testing "use platform if set explicitly"
    (let [client (assoc base-client :platform "novelty/architecture64")]
      (is (= "novelty/architecture64" (:platform (client/start client))))))
  (testing "fetch engine platform if none given"
    (let [client (assoc base-client :platform nil)]
      (is (string? (:platform (client/start client)))))))

(deftest ^:docker t-client-connection-failure
  (testing "missing unix socket"
    (let [client (assoc base-client :uri "unix:///tmp/unknown-socket.sock")]
      (is (thrown-with-msg?
            Exception
            #"Could not connect to docker"
            (client/start client)))))
  (testing "unavailable port"
    (let [client (assoc base-client :uri "tcp://localhost:12346")]
      (is (thrown-with-msg?
            Exception
            #"Could not connect to docker"
            (client/start client))))))
