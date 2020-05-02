(ns into.build.write-artifacts
  (:require [into.flow
             [core :as flow]]
            [into.utils
             [data :as data]
             [log :as log]]
            [into.docker :as docker]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [org.apache.commons.compress.archivers.tar
            TarArchiveEntry
            TarArchiveInputStream]
           [java.io InputStream File]
           [java.nio.file.attribute PosixFilePermissions]
           [java.nio.file Files]))

;; ## Write Artifacts

(defn- tar-seq
  [^TarArchiveInputStream tar]
  (when-let [entry (.getNextTarEntry tar)]
    (cons entry (lazy-seq (tar-seq tar)))))

(defn- target-file
  "Create target file. Since we're operating on a TAR archive of a
   container directory, we drop the base directory name."
  ^File [^File target ^TarArchiveEntry e]
  (as-> e <>
    (.getName <>)
    (string/replace <> #"^[^/]+/" "")
    (io/file target <>)))

(defn- ensure-parent!
  [^File out]
  (let [parent (.getParentFile out)]
    (when-not (.isDirectory parent)
      (.mkdirs parent))))

(defn- extract-tar-stream!
  [data ^TarArchiveInputStream tar ^File target]
  (doseq [^TarArchiveEntry e (tar-seq tar)
          :when (.isFile e)
          :let [out (target-file target e)]]
    (log/info data "|   %s" (.getPath out))
    (ensure-parent! out)
    (io/copy tar out)))

(defn- write-artifacts!
  [{:keys [client] :as data} path]
  (let [target (io/file path)]
    (when-not (.isDirectory target)
      (.mkdirs target))
    (with-open [^InputStream in (docker/read-container-archive!
                                 client
                                 (data/instance-container data :builder)
                                 (data/path-for data :artifact-directory))
                tar (TarArchiveInputStream. in)]
      (extract-tar-stream! data tar target)))
  data)

;; ## Flow

(defn run
  [data]
  (if-let [path (get-in data [:spec :target-path])]
    (flow/with-flow-> data
      (log/emph "Writing artifacts to '%s' ..." path)
      (write-artifacts! path))
    data))
