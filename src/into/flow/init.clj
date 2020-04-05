(ns into.flow.init
  (:require [into.flow.core :as flow]
            [clojure.java.io :as io]))

(defn run
  [data]
  (flow/with-flow-> data
    (flow/validate
     [:spec :source-path]
     #(-> % io/file .isDirectory)
     "Path does not point at a directory.")))
