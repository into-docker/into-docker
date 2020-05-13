(ns into.test.files
  (:require [clojure.java.io :as io])
  (:import [java.io File]))

(defn create-files!
  [target paths]
  (doseq [path paths
          :let [f (io/file target path)]]
    (when (.isDirectory f)
      (throw
        (IllegalStateException.
          (format
            (str "Could not create file '%s' since it was already previously "
                 "created as a directory by one of: %s"
                 path
                 paths)))))
    (some-> f (.getParentFile) (.mkdirs))
    (spit f "")))

(defn with-temp-dir*
  [paths f]
  (let [target (doto (File/createTempFile "into-docker-test" "")
                 (.delete)
                 (.mkdir))]
    (try
      (create-files! target paths)
      (f target)
      (finally
        (doseq [f (reverse (file-seq target))]
          (io/delete-file f))
        (.delete target)))))

(defmacro with-temp-dir
  [[sym paths] & body]
  `(with-temp-dir*
     ~paths
     (fn [~(with-meta sym {:tag `java.io.File})]
       ~@body)))
