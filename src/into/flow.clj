(ns into.flow
  (:require [into.flow
             [assemble :as assemble]
             [build :as build]
             [cleanup :as cleanup]
             [core :as flow]
             [collect :as collect]
             [commit :as commit]
             [create-cache :as create-cache]
             [init :as init]
             [pull :as pull]
             [read-build-profile :as read-build-profile]
             [read-cache-paths :as read-cache-paths]
             [restore-cache :as restore-cache]
             [start :as start]
             [transfer :as transfer]]))

(defn run
  [data]
  (-> (flow/with-flow-> data
        (init/run)
        (pull/run)
        (start/run)
        (read-build-profile/run)
        (transfer/pre-run)
        (read-cache-paths/run)
        (restore-cache/run)
        (collect/run)
        (build/run)
        (transfer/run)
        (assemble/run)
        (commit/run)
        (create-cache/run))
      (cleanup/run)))
