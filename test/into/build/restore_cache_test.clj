(ns into.build.restore-cache-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging.test :refer [with-log]]
            [clojure.java.io :as io]
            [into.constants :as constants]
            [into.docker :as docker]
            [into.docker.mock :as mock]
            [into.build.restore-cache :as restore-cache]
            [into.test.files :refer [with-temp-dir]]))

;; ## Fixtures

(def ^:private cache-file-contents
  "DATA")

;; ## Helpers

(defn- cache-target-container
  [exit-code]
  (let [events (volatile! [])
        container (reify docker/DockerContainer
                    (run-container-command [this _]
                      (vswap! events conj [:run-command])
                      (mock/->MockExec this []  {:exit exit-code}))
                    (stream-into-container [_ directory in]
                      (vswap! events conj [:stream directory (slurp in)])))]
    [events container]))

(defn- setup-cache-file!
  ^java.io.File [tmp]
  (doto (io/file tmp "cache.gz")
    (spit cache-file-contents)))

;; ## Tests

(deftest t-restore-cache
  (with-log
    (with-temp-dir [tmp []]
      (let [[events builder] (cache-target-container 0)
            data     {:spec
                      {:cache-from (setup-cache-file! tmp)}
                      :builder-container builder
                      :cache-paths       ["/some/file"]}
            data'    (restore-cache/run data)]
        (is (= data' data))
        (is (= [[:stream
                 (constants/path-for :working-directory)
                 cache-file-contents]
                [:run-command]]
               @events))))))

(deftest t-restore-cache-does-nothing-without-cache-paths
  (with-log
    (with-temp-dir [tmp []]
      (let [[events builder] (cache-target-container 0)
            data     {:spec
                      {:cache-from (setup-cache-file! tmp)}
                      :builder-container builder
                      :cache-paths       []}
            data'    (restore-cache/run data)]
        (is (= data' data))
        (is (empty?  @events))))))

(deftest t-restore-cache-does-nothing-without-cache-from
  (with-log
    (let [[events builder] (cache-target-container 0)
          data     {:spec              {}
                    :builder-container builder
                    :cache-paths       ["/some/file"]}
          data'    (restore-cache/run data)]
      (is (= data' data))
      (is (empty?  @events)))))

(deftest t-restore-cache-does-nothing-when-cache-file-missing
  (with-log
    (with-temp-dir [tmp []]
      (let [[events builder] (cache-target-container 0)
            data     {:spec              {:cache-from (doto (setup-cache-file! tmp)
                                                        (.delete))}
                      :builder-container builder
                      :cache-paths       ["/some/file"]}
            data'    (restore-cache/run data)]
        (is (= data' data))
        (is (empty?  @events))))))

(deftest t-restore-cache-failure
  (with-log
    (with-temp-dir [tmp []]
      (let [[_ builder]  (cache-target-container 1)
            data     {:spec              {:cache-from (setup-cache-file! tmp)}
                      :builder-container builder
                      :cache-paths       ["/some/file"]}]
        (is (thrown-with-msg?
              IllegalStateException
              #"Exec in container (.+) failed!"
              (restore-cache/run data)))))))
