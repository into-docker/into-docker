(ns into.utils.cache
  "Caching helpers. The caching concept is:
   - Create a hash for each path-to-cache
   - Move all paths into the cache directory using the hash as name
   - Export TAR from the container.
   Reverse to restore."
  (:require [into.utils.data :as data]
            [clojure.string :as string]
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

(defn prepare-cache-command
  [cache-directory paths]
  (->> paths
       (map #(format "mv -f '%s' '%s/%s'"
                     %
                     cache-directory
                     (sha1 %)))
       (string/join "; ")
       (format "mkdir -p '%s'; %s" cache-directory)
       (vector "sh" "-c")))

(defn restore-cache-command
  [cache-directory paths]
  (->> paths
       (map #(format "rm -rf '%s' && mv -f '%s/%s' '%s'"
                     %
                     cache-directory
                     (sha1 %)
                     %))
       (string/join "; ")
       (vector "sh" "-c")))

(defn default-cache-file
  [target-image]
  (let [dir (io/file
              (or (System/getenv "HOME")
                  (System/getProperty "user.home")
                  (throw
                    (IllegalStateException.
                      "Could not find HOME directory for cache creation.")))
              ".cache"
              "into-docker")]
    (.mkdirs dir)
    (io/file dir (-> target-image
                     (data/->image)
                     (:full-name)
                     (sha1)
                     (str ".tar")))))
