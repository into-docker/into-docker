(ns into.docker
  (:require [potemkin :refer [defprotocol+]]))

;; ## Raw Client

(defprotocol+ DockerClient
  (pull-image [this image])
  (inspect-image [this image])

  (run-container [this container-name image])
  (commit-container [this container data])
  (cleanup-container [this container])

  (read-container-file! [this container path])
  (copy-from-container! [this tar-stream container path])
  (copy-into-container! [this tar-stream container path])
  (copy-between-containers! [this from to from-path to-path])
  (execute-command! [this container data log-fn]))

;; ## Derived Functionality

(defn mkdir
  [client container path & more]
  (execute-command!
   client
   container
   {:cmd (concat ["mkdir" "-p" path] more)}
   (constantly nil)))
