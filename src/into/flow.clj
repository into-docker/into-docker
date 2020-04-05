(ns into.flow
  (:require [into.flow
             [assemble :as assemble]
             [build :as build]
             [cleanup :as cleanup]
             [core :as flow]
             [collect :as collect]
             [commit :as commit]
             [init :as init]
             [pull :as pull]
             [start :as start]
             [transfer :as transfer]]))

(defn run
  [data]
  (-> (flow/with-flow-> data
        (init/run)
        (pull/run)
        (start/run)
        (collect/run)
        (build/run)
        (transfer/run)
        (assemble/run)
        (commit/run))
      (cleanup/run)))
