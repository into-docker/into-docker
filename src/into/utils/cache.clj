(ns into.utils.cache
  "Caching helpers. The caching concept is:
   - Create a hash for each path-to-cache
   - Move all paths into the cache directory using the hash as name
   - Export TAR from the container.
   Reverse to restore."
  (:require [clojure.string :as string]
            [clojure.java.io :as io])
  (:import [java.security MessageDigest]))

(defn- sha1
  "We need to create a fingerprint for cache paths that we can use
   inside a TAR file as directory name."
  [^String s]
  (let [instance (MessageDigest/getInstance "SHA1")
        ^bytes data (.getBytes s "UTF-8")]
    (->> (.digest instance data)
         (map #(format "%02x" %))
         (apply str))))

(defn- ->prepare-command
  [cache-directory path]
  (let [cache-path (.getPath (io/file cache-directory (sha1 path)))]
    (format
     (str "_CPTH_='%s'; _PTH_='%s';"
          "[ -e \"$_PTH_\" ] && ("
          "mv \"$_PTH_\" \"$_CPTH_\" && "
          "echo \"$_PTH_\";"
          ")")
     cache-path
     path)))

(defn prepare-cache-command
  [cache-directory paths]
  (->> paths
       (map #(->prepare-command cache-directory %))
       (string/join "; ")
       (format "mkdir -p '%s'; %s" cache-directory)
       (format "%s; true")
       (vector "sh" "-c")))

(defn- ->restore-command
  [cache-directory path]
  (let [cache-path (.getPath (io/file cache-directory (sha1 path)))]
    (format
     (str "_CPTH_='%s'; _PTH_='%s';"
          "[ -e \"$_CPTH_\" ] && ("
          "rm -rf \"$_PTH_\";"
          "mv \"$_CPTH_\" \"$_PTH_\" && "
          "echo \"$_PTH_\";"
          "rm -rf \"$_CPTH_\""
          ")")
     cache-path
     path)))

(defn restore-cache-command
  [cache-directory paths]
  (->> paths
       (map #(->restore-command cache-directory %))
       (string/join "; ")
       (format "%s; true")
       (vector "sh" "-c")))
