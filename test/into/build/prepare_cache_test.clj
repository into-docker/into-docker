(ns into.build.prepare-cache-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging.test :refer [with-log]]
            [clojure.java.io :as io]
            [into.docker :as docker]
            [into.docker.mock :as mock]
            [into.build.prepare-cache :as prepare-cache]
            [into.test.files :refer [with-temp-dir]]))

;; ## Helpers

(defn- cache-source-container
  [exit-code]
  (reify docker/DockerContainer
    (run-container-command [this _]
      (mock/->MockExec this []  {:exit exit-code}))))

;; ## Tests

(deftest t-prepare-cache
  (with-log
    (with-temp-dir [tmp []]
      (let [cache-to (io/file tmp "cache.gz")
            builder  (cache-source-container 0)
            data     {:spec              {:cache-to cache-to}
                      :builder-container builder
                      :cache-paths       ["/some/file"]}
            data'    (prepare-cache/run data)]
        (is (= data' data))))))

(deftest t-prepare-cache-with-cache-volume
  (with-log
    (let [builder  (cache-source-container 0)
          data     {:spec              {:use-cache-volume? true
                                        :use-volumes? true}
                    :builder-container builder
                    :cache-paths       ["/some/file"]}
          data'    (prepare-cache/run data)]
      (is (= data' data)))))

(deftest t-prepare-cache-does-nothing-without-cache-paths
  (with-log
    (with-temp-dir [tmp []]
      (let [cache-to (io/file tmp "cache.gz")
            builder  (cache-source-container 0)
            data     {:spec              {:cache-to cache-to}
                      :builder-container builder
                      :cache-paths       []}
            data'    (prepare-cache/run data)]
        (is (= data' data))))))

(deftest t-prepare-cache-failure
  (with-log
    (with-temp-dir [tmp []]
      (let [cache-to (io/file tmp "cache.gz")
            builder  (cache-source-container 1)
            data     {:spec              {:cache-to cache-to}
                      :builder-container builder
                      :cache-paths       ["/some/file"]}]
        (is (thrown-with-msg?
              IllegalStateException
              #"Exec in container (.+) failed!"
              (prepare-cache/run data)))))))

(deftest t-prepare-cache-does-nothing-without-cache-to-or-volume
  (with-log
    (let [builder  (cache-source-container 1)
          data     {:spec              {}
                    :builder-container builder
                    :cache-paths       ["/some/file"]}
          data'    (prepare-cache/run data)]
      (is (= data' data)))))
