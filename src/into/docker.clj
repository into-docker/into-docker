(ns into.docker
  (:require [potemkin :refer [defprotocol+]]))

(defprotocol+ DockerClient
  (pull-image [this image])
  (inspect-image [this image])

  (run-container [this container-name image])
  (commit-container [this container image cmd])
  (cleanup-container [this container])

  (copy-into-container! [this tar-stream container path])
  (copy-between-containers! [this from to from-path to-path])
  (execute-command! [this container command env log-fn]))
