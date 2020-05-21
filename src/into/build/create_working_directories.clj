(ns into.build.create-working-directories
  (:require [into
             [constants :as constants]
             [flow :as flow]
             [docker :as docker]]))

;; ## Logic

(defn- chown-directories!
  "We need to make sure that the directories we create are readable
   by the image user (if the user is not root)."
  [container user path-keys]
  (apply docker/chown
         container
         user
         (map constants/path-for path-keys)))

(defn- create-directories!
  [container path-keys]
  (apply docker/mkdir
         container
         (map constants/path-for path-keys)))

(defn- create-working-directories!
  [data container-key image-key path-keys]
  (when-let [container (get data container-key)]
    (create-directories! container path-keys)
    (when-let [user (get-in data [image-key :user])]
      (chown-directories! container user path-keys)))
  data)

;; ## Flow

(defn run
  "Create working directories (source, artifacts, cache) in containers."
  [data]
  (flow/with-flow-> data
    (create-working-directories! :builder-container
                                 :builder-image
                                 [:source-directory
                                  :artifact-directory
                                  :cache-directory])
    (create-working-directories! :runner-container
                                 :runner-image
                                 [:working-directory])))
