(ns into.build.init
  (:require [into.flow
             [core :as flow]
             [log :as log]]
            [into.utils.data :as data]
            [clojure.java.io :as io]))

(defn run
  [{:keys [spec] :as data}]
  (flow/with-flow-> data
    (log/emph "Building image [%s] from '%s' ..."
              (get-in spec [:target-image :full-name])
              (get-in spec [:source-path]))
    (flow/validate
     [:spec :source-path]
     #(-> % io/file .isDirectory)
     "Path does not point at a directory.")))
