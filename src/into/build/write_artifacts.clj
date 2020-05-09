(ns into.build.write-artifacts
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]
            [into.docker.tar :refer [untar]]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io InputStream]))

;; ## Write Artifacts

(defn- target-file-fn
  "Create target file. Since we're operating on a TAR archive of a
   container directory, we drop the base directory name."
  [base-path file-path]
  (as-> file-path <>
    (string/replace <> #"^[^/]+/" "")
    (io/file base-path <>)))

(defn- write-artifacts!
  [{:keys [builder-container]} path]
  (with-open [^InputStream in (docker/stream-from-container
                               builder-container
                               (constants/path-for :artifact-directory))]
    (untar in path target-file-fn)))

;; ## Flow

(defn run
  "Write artifacts to the desired path."
  [data]
  (when-let [path (get-in data [:spec :artifact-path])]
    (log/emph "Writing artifacts to '%s' ..." path)
    (write-artifacts! data path))
  data)
