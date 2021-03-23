(ns into.build.read-buildenv-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [into.test.files :refer [with-temp-dir]]
            [into.build.read-buildenv :as read-buildenv]))

;; ## Fixtures

(def buildenv-variables
  "Variables that should always be available when these tests are run."
  ["HOME" "PATH"])

(defn run-read-buildenv!
  [dir envs]
  (doto (io/file dir ".buildenv")
    (spit (string/join "\n" envs)))
  (read-buildenv/run {:spec {:source-path dir}}))

;; ## Tests

(deftest t-read-buildenv
  (with-temp-dir [dir [".buildenv"]]
    (let [{:keys [error builder-env]} (run-read-buildenv! dir buildenv-variables)]
      (is (not error))
      (is (= buildenv-variables
             (map (comp first #(string/split % #"=")) builder-env))))))

(deftest t-read-buildenv-with-empty-file
  (with-temp-dir [dir [".buildenv"]]
    (let [{:keys [error builder-env]} (run-read-buildenv! dir [])]
      (is (not error))
      (is (empty? builder-env)))))

(deftest t-read-buildenv-with-missing-file
  (with-temp-dir [dir [".buildenv"]]
    (let [{:keys [error builder-env]} (read-buildenv/run {:spec {:source-path dir}})]
      (is (not error))
      (is (empty? builder-env)))))

(deftest t-read-buildenv-throws-if-missing
  (with-temp-dir [dir [".buildenv"]]
    (let [{:keys [^Exception error builder-env]}
          (run-read-buildenv! dir ["ABSOLUTELY_UNKNOWN_TEST_VARIABLE"])]
      (is (empty? builder-env))
      (is (re-matches
            #"Required environment variables are missing: ABSOLUTELY_UNKNOWN_TEST_VARIABLE.*"
            (.getMessage error))))))
