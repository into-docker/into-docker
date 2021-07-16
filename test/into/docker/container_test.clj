(ns into.docker.container-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [into.docker :as docker]
            [into.docker.tar :as tar]
            [into.docker.client :as client])
  (:import (java.util UUID)))

;; ## Fixtures

(def ^:private base-client
  (client/make-from-env))

(def ^:private container-name
  "into-docker-test-container")

(def ^:private container-image
  "busybox:1.33.0")

;; ## Lifecycle

(def ^:dynamic client)
(def ^:dynamic container)

(defmacro silently
  [form]
  `(try
     (do ~form)
     (catch Exception _#)))

(use-fixtures
  :each
  (fn [f]
    (binding [client (client/start base-client)]
      (let [image  (-> (docker/pull-image-record client container-image)
                       (assoc :volumes
                              [{:path "/tmp/volume-ext"
                                :name (str container-name "-vol")
                                :retain? false}]))]
        (binding [container (docker/container client container-name image)]
          (docker/run-container container)
          (try
            (f)
            (finally
              (silently (docker/cleanup-container container))
              (silently (docker/cleanup-volumes container)))))))))

;; ## Test

(deftest ^:docker t-container
  (is (= "busybox" (str container)))
  (is (= container-name (docker/container-name container)))
  (is (= "root" (docker/container-user container))))

(deftest ^:docker t-run-container-command
  (let [exec (docker/run-container-command
               container
               {:cmd ["sh" "-c" "echo $STRING_TO_ECHO; exit 78"]
                :env ["STRING_TO_ECHO=1234"]})
        stdout (docker/read-exec-stdout exec)
        result (docker/exec-result exec)]
    (is (= container (docker/exec-container exec)))
    (is (= "1234\n" stdout))
    (is (= 78 (:exit result)))))

(deftest ^:docker t-stream-container
  (let [content   "DATA"
        to-path   "/tmp"
        from-path (str to-path "/data")
        sources   [{:source (.getBytes content "UTF-8")
                    :path   "data/content.txt"
                    :length (count content)}]
        tar (tar/tar sources)]
    (testing "stream-into-container"
      (with-open [in (io/input-stream tar)]
        (is (docker/stream-into-container container to-path in))))
    (testing "stream-from-container"
      (with-open [in (docker/stream-from-container container from-path)]
        (let [unwrap (fn [s] (update s :source #(String. ^bytes % "UTF-8")))]
          (is (= (map unwrap sources)
                 (map unwrap (tar/untar-seq in)))))))))

(deftest ^:docker t-commit-container
  (let [commit-uuid  (str (UUID/randomUUID))
        target-image {:name (str container-name "-committed")
                      :tag  "latest"
                      :cmd  "echo"
                      :labels {"commit-uuid" commit-uuid}}
        result (docker/commit-container container target-image)]
    (is result)
    (is (= commit-uuid
           (->> (str (:name target-image) ":latest")
                (docker/inspect-image client)
                (:Config)
                (:Labels)
                (:commit-uuid))))))

(deftest ^:docker t-error-handling
  (docker/cleanup-container container)
  (is (thrown-with-msg?
        IllegalStateException
        #"Operation failed:"
        (docker/cleanup-container container))))

(deftest ^:docker t-exec-and-wait
  (let [result (docker/exec-and-wait
               container
               {:cmd ["sh" "-c" "echo $STRING_TO_ECHO; exit 0"]
                :env ["STRING_TO_ECHO=1234"]})]
    (is (zero? (:exit result)))))

(deftest ^:docker t-exec-and-wait-failure
  (is (thrown-with-msg?
        IllegalStateException
        #"Exec in container \(busybox\) failed!"
        (docker/exec-and-wait container {:cmd ["exit" "1"]}))))
