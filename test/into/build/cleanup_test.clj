(ns into.build.cleanup-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging.test :refer [with-log]]
            [into.build.cleanup :as cleanup]
            [into.docker.mock :as mock]
            [into.docker :as docker]))

(deftest t-cleanup
  (with-log
    (let [builder (mock/running-container)
          runner  (mock/running-container)
          data    {:builder-container builder
                   :runner-container runner}
          data' (cleanup/run data)]
      (is (= data data'))
      (is (= [:run :cleanup] (mock/events builder)))
      (is (= [:run :cleanup] (mock/events runner))))))

(deftest t-cleanup-volumes
  (with-log
    (let [builder (mock/running-container)
          runner  (mock/running-container)
          data    {:spec {:use-volumes? true}
                   :builder-container builder
                   :runner-container runner}
          data' (cleanup/run data)]
      (is (= data data'))
      (is (= [:run :cleanup :cleanup-volumes] (mock/events builder)))
      (is (= [:run :cleanup] (mock/events runner))))))

(deftest t-cleanup-error
  (with-log
    (let [ex      (IllegalStateException.)
          data    {:builder-container
                   (reify docker/DockerContainer
                     (cleanup-container [_]
                       (throw ex))) }
          data'   (cleanup/run data)]
      (is (= ex (:cleanup-error data'))))))
