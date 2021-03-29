(ns into.build.add-runner-env-test
  (:require [clojure.test :refer [deftest is]]
            [into.build.add-runner-env :as add-runner-env]))

(deftest t-add-runner-env
  (let [data (add-runner-env/run {:runner-container {}})]
    (is (= ["INTO_ARTIFACT_DIR=/tmp/artifacts"]
           (:runner-env data)))))

(deftest t-add-runner-env-does-nothing-without-runner-container
  (let [data (add-runner-env/run {})]
    (is (not (contains? data :runner-env)))))
