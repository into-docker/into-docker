(ns into.build.assemble-script-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging.test :refer [with-log]]
            [into.build.assemble-script :as assemble-script]
            [into.constants :as constants]
            [into.docker.mock :as mock]))

;; ## Test Script

(defn exec-assemble
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

(deftest t-assemble-script
  (with-log
    (let [container (-> (mock/container)
                        (mock/add-file
                          (constants/path-for :assemble-script-runner)
                          `(exec-assemble 0)))]
      (assemble-script/run {:runner-container container
                            :runner-env ["INTO_ARTIFACTS_PATH=/bin"]})
      (is (contains? (mock/list-files container "/bin") "/bin/run")))))

(deftest t-assemble-script-does-nothing-without-runner-container
  (is (= {} (assemble-script/run {}))))

(deftest t-assemble-script-fails
  (with-log
    (let [container (-> (mock/container)
                        (mock/add-file
                          (constants/path-for :assemble-script-runner)
                          `(exec-assemble 1)))]
      (is (thrown-with-msg?
            IllegalStateException
            #"Exec in container \(.+\) failed"
            (assemble-script/run {:runner-container container}))))))
