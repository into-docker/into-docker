(ns into.build.create-cache-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging.test :refer [with-log]]
            [clojure.java.io :as io]
            [into.docker :as docker]
            [into.docker.mock :as mock]
            [into.build.create-cache :as create-cache]
            [into.test.files :refer [with-temp-dir]])
  (:import (java.util.zip GZIPInputStream)))

;; ## Helpers

(defn- cache-source-container
  [exit-code ^String content]
  (reify docker/DockerContainer
    (run-container-command [this _]
      (mock/->MockExec this []  {:exit exit-code}))
    (stream-from-container [_ _]
      (io/input-stream (.getBytes content "UTF-8")))))

;; ## Tests

(deftest t-create-cache
  (with-log
    (with-temp-dir [tmp []]
      (let [content  "TEST"
            cache-to (io/file tmp "cache.gz")
            builder  (cache-source-container 0 content)
            data     {:spec              {:cache-to cache-to}
                      :builder-container builder
                      :cache-paths       ["/some/file"]}
            data'    (create-cache/run data)]
        (is (= data' data))
        (with-open [in (io/input-stream cache-to)
                    gz (GZIPInputStream. in)]
          (is (= content (slurp gz))))))))

(deftest t-create-cache-does-nothing-without-cache-paths
  (with-log
    (with-temp-dir [tmp []]
      (let [content  "TEST"
            cache-to (io/file tmp "cache.gz")
            builder  (cache-source-container 0 content)
            data     {:spec              {:cache-to cache-to}
                      :builder-container builder
                      :cache-paths       []}
            data'    (create-cache/run data)]
        (is (= data' data))
        (is (not (.isFile cache-to)))))))

(deftest t-create-cache-failure
  (with-log
    (with-temp-dir [tmp []]
      (let [content  "TEST"
            cache-to (io/file tmp "cache.gz")
            builder  (cache-source-container 1 content)
            data     {:spec              {:cache-to cache-to}
                      :builder-container builder
                      :cache-paths       ["/some/file"]}]
        (is (thrown-with-msg?
              IllegalStateException
              #"Exec in container (.+) failed!"
              (create-cache/run data)))))))

(deftest t-create-cache-does-nothing-without-cache-to
  (with-log
    (let [content  "TEST"
          builder  (cache-source-container 1 content)
          data     {:spec              {}
                    :builder-container builder
                    :cache-paths       ["/some/file"]}
          data'    (create-cache/run data)]
      (is (= data' data)))))
