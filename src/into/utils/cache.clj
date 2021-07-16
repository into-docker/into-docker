(ns into.utils.cache
  "Caching helpers. The caching concept is:
   - Create a hash for each path-to-cache
   - Move all paths into the cache directory using the hash as name
   - Export TAR from the container.
   Reverse to restore."
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [into.utils.sha1 :refer [sha1]]))

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

(defn prepare-cache-commands
  [cache-directory paths]
  (map #(->prepare-command cache-directory %) paths))

(defn prepare-cache-command
  [cache-directory paths]
  (->> (prepare-cache-commands cache-directory paths)
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

(defn restore-cache-commands
  [cache-directory paths]
  (map #(->restore-command cache-directory %) paths))

(defn restore-cache-command
  [cache-directory paths]
  (->> (restore-cache-commands cache-directory paths)
       (string/join "; ")
       (format "%s; true")
       (vector "sh" "-c")))

;; ## Utility

(defn cache-file-exists?
  [spec]
  (some-> spec :cache-from io/file (.isFile)))


(defn cache-file-used?
  [spec]
  (some? (:cache-to spec)))

(defn cache-volume-used?
  [spec]
  (and (:use-cache-volume? spec)
       (:use-volumes? spec)))
