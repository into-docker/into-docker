(ns into.build.collect-sources-test
  (:require [clojure.test :refer [deftest is]]
            [into.test.files :refer [with-temp-dir]]
            [into.build.collect-sources :as collect-sources]))

(deftest t-collect-sources
  (let [sources ["a" "b" "c"]]
    (with-temp-dir [dir sources]
      (let [{:keys [source-paths error]}
            (collect-sources/run {:spec {:source-path (.getPath dir)}})]
        (is (nil? error))
        (is (= (set sources) (set source-paths)))))))

(deftest t-collect-sources-fails-if-no-files
  (with-temp-dir [dir []]
    (let [{:keys [source-paths ^Exception error]}
          (collect-sources/run {:spec {:source-path (.getPath dir)}})]
      (is (empty? source-paths))
      (is (re-matches #"^No source files found in.*"
                      (.getMessage error))))))


(deftest t-collect-sources-fails-if-everything-is-ignored
  (with-temp-dir [dir ["a" "aa" "aaa"]]
    (let [{:keys [source-paths ^Exception error]}
          (collect-sources/run
            {:spec         {:source-path (.getPath dir)}
             :ignore-paths ["a*"]})]
      (is (empty? source-paths))
      (is (re-matches #"^No source files found in.*"
                      (.getMessage error))))))
