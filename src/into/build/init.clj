(ns into.build.init
  (:require [into.flow
             [core :as flow]]
            [into.utils
             [data :as data]
             [log :as log]]
            [clojure.java.io :as io]))

(defn- validate
  [data]
  (flow/with-flow-> data
    (flow/validate
     [:spec :source-path]
     #(-> % io/file .isDirectory)
     "Path does not point at a directory.")))

(defn for-image
  [{:keys [spec] :as data}]
  (flow/with-flow-> data
    (log/emph "Building image [%s] from '%s' ..."
              (get-in spec [:target-image :full-name])
              (get-in spec [:source-path]))
    (validate)))

(defn for-artifacts
  [{:keys [spec] :as data}]
  (flow/with-flow-> data
    (log/emph "Building artifacts from '%s' ..."
              (get-in spec [:source-path]))
    (validate)))
