(ns into.build.flow
  (:require [into.flow.core :as flow]
            [into.build
             [assemble-script :as assemble]
             [build-script :as build]
             [ci :as ci]
             [cleanup :as cleanup]
             [collect :as collect]
             [commit :as commit]
             [create-cache :as create-cache]
             [init :as init]
             [pull :as pull]
             [read-build-profile :as read-build-profile]
             [read-cache-paths :as read-cache-paths]
             [restore-cache :as restore-cache]
             [start :as start]
             [transfer :as transfer]
             [write-artifacts :as write-artifacts]]
            [into.utils.log :as log]))

;; ## Phases

(defn- prepare
  [data]
  (flow/with-flow-> data
    (ci/run)
    (pull/run)))

(defn- build
  [data]
  (flow/with-flow-> data
    (read-build-profile/run)
    (read-cache-paths/run)
    (restore-cache/run)
    (collect/run)
    (build/run)))

(defn- assemble
  [data]
  (flow/with-flow->
    (transfer/run)
    (assemble/run)
    (commit/run)))

(defn- finalise
  [data]
  (flow/with-flow-> data
    (write-artifacts/run)
    (create-cache/run)))

;; ## Flows

(defn- run-for-image
  [data]
  (flow/with-flow-> data
    (init/for-image)
    (prepare)
    (start/for-image)
    (transfer/pre-run)
    (build)
    (assemble)
    (finalise)))

(defn- run-for-artifacts
  [data]
  (flow/with-flow-> data
    (init/for-artifacts)
    (prepare)
    (start/for-artifacts)
    (build)
    (finalise)))

;; ## Run Flows

(defn- create-image?
  [{:keys [spec]}]
  (contains? spec :target-image))

(defn- report-success
  [data]
  (if (create-image? data)
    (log/success
     data
     "Image [%s] has been built successfully."
     (get-in data [:spec :target-image :full-name]))
    (log/success
     data
     "Artifacts have been written to '%s'."
     (get-in data [:spec :target-path]))))

(defn run
  [data]
  (-> (if (create-image? data)
        (run-for-image data)
        (run-for-artifacts data))
      (cleanup/run)
      (flow/with-flow->
        (report-success))))
