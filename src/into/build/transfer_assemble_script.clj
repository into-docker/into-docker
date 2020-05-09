(ns into.build.transfer-assemble-script
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [log :as log]]))

(defn run
  "This moves the assemble script. This is done before any user-provided
   logic is called to prevent changes to the assemble script and thus retain
   the integrity of running it as root in the runner container."
  [{:keys [builder-container runner-container] :as data}]
  (if runner-container
    (let [from-path (constants/path-for :assemble-script)
          to-path (constants/path-for :working-directory)]
      (log/trace "Transferring assemble script [%s:%s -> %s:%s] ..."
                 builder-container
                 from-path
                 runner-container
                 to-path)
      (docker/transfer-between-containers
       builder-container
       runner-container
       from-path
       to-path)
      data)
    data))
