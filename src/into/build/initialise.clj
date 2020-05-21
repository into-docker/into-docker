(ns into.build.initialise
  (:require [into.log :as log]))

(defn run
  [{:keys [spec] :as data}]
  (let [{:keys [target-image-name
                artifact-path
                source-path]}
        spec]
    (cond target-image-name
          (log/emph "Building image [%s] from '%s' ..."
                    target-image-name
                    source-path)

          artifact-path
          (log/emph "Building artifacts from '%s' ..."
                    source-path))
    (assoc data :started-at (System/nanoTime))))
