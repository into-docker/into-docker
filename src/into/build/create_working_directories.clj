(ns into.build.create-working-directories
  (:require [into
             [constants :as constants]
             [flow :as flow]
             [docker :as docker]]))

;; ## Logic

(defn- create-working-directories!
  [data container-key & path-keys]
  (if-let [container (get data container-key)]
    (do
      (apply docker/mkdir
             container
             (map constants/path-for path-keys))
      data)
    data))

;; ## Flow

(defn run
  "Create working directories (source, artifacts, cache) in containers."
  [data]
  (flow/with-flow-> data
    (create-working-directories! :builder-container
                                 :source-directory
                                 :artifact-directory
                                 :cache-directory)
    (create-working-directories! :runner-container
                                 :working-directory)))
