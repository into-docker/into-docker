(ns into.utils.version-test
  (:require [into.utils.version :as version]
            [clojure.test :refer [deftest testing is]]
            [clojure.java.shell :as sh]
            [clojure.string :as string]))

(deftest t-read-revision
  (testing "returns revision (needs git)"
    (is (string? (version/read-revision {}))))
  (testing "returns nil if command fails"
    (with-redefs [sh/sh (constantly {:exit 1})]
      (is (nil? (version/read-revision {})))))
  (testing "returns nil if command throws"
    (with-redefs [sh/sh (fn [& _]
                          (throw
                           (ex-info "FAILURE" {})))]
      (is (nil? (version/read-revision {}))))))

(deftest t-current-version-and-revision
  (is (not (string/blank? (version/current-version))))
  (is (not (string/blank? (version/current-revision)))))
