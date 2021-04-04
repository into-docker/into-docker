(ns into.build.build-script-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging.test :refer [with-log]]
            [into.build.build-script :as build-script]
            [into.constants :as constants]
            [into.docker.mock :as mock]))

;; ## Test Script

(defn exec-build
  [exit-code]
  (fn [{:keys [container env]}]
    (when (zero? exit-code)
      (mock/add-file container
                     (str (env "INTO_ARTIFACTS_PATH") "/run")
                     "exit"))
    (mock/->MockExec
      container
      [[:stderr "Working..."]
       [:stdout "OK."]]
      {:exit exit-code})))

;; ## Tests

(deftest t-build-script
  (with-log
    (let [container (-> (mock/container)
                        (mock/add-file
                          (constants/path-for :build-script)
                          `(exec-build 0)))]
      (build-script/run {:builder-container container
                         :builder-env ["INTO_ARTIFACTS_PATH=/bin"]})
      (is (contains? (mock/list-files container "/bin") "/bin/run")))))

(deftest t-build-script-fails
  (with-log
    (let [container (-> (mock/container)
                        (mock/add-file
                          (constants/path-for :build-script)
                          `(exec-build 1)))]
      (is (thrown-with-msg?
            IllegalStateException
            #"Exec in container \(.+\) failed"
            (build-script/run {:builder-container container}))))))
