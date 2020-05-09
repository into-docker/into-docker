(ns into.build.validate-spec
  (:require [into.flow :as flow]
            [clojure.java.io :as io]))

(defn- directory?
  [^String v]
  (some-> v io/file .isDirectory))

(defn- verify-targets
  [{:keys [spec] :as data}]
  (if-not (or (:target-image-name spec)
              (:artifact-path spec))
    (flow/fail data "You need to supply either '--tag' or '--write-artifacts'.")
    data))

(defn run
  [data]
  (-> data
      (flow/validate
       [:spec :source-path]
       directory?
       "Path does not point to a directory.")
      (verify-targets)))
