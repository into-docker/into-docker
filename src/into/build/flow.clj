(ns into.build.flow
  (:require [into.flow :as flow]
            [into.build
             [add-builder-env :as add-builder-env]
             [add-default-labels :as add-default-labels]
             [add-oci-labels :as add-oci-labels]
             [add-runner-env :as add-runner-env]
             [assemble-script :as assemble-script]
             [build-script :as build-script]
             [cleanup :as cleanup]
             [collect-sources :as collect-sources]
             [commit :as commit]
             [create-cache :as create-cache]
             [create-working-directories :as create-working-directories]
             [initialise :as initialise]
             [inject-sources :as inject-sources]
             [prepare-target-image :as prepare-target-image]
             [pull :as pull]
             [read-build-profile :as read-build-profile]
             [read-cache-paths :as read-cache-paths]
             [read-ignore-paths :as read-ignore-paths]
             [restore-cache :as restore-cache]
             [start :as start]
             [transfer-artifacts :as transfer-artifacts]
             [transfer-assemble-script :as transfer-assemble-script]
             [write-artifacts :as write-artifacts]
             [validate-spec :as validate-spec]]))

;; ## Flow

(defn run
  [data]
  (-> (flow/with-flow-> data
        (initialise/run)
        (validate-spec/run)

        ;; --- Create the build environment (builder, runner, target)
        (pull/run)
        (prepare-target-image/run)
        (add-default-labels/run)
        (add-oci-labels/run)
        (start/run)

        ;; --- Prepare the builder and runner
        (read-build-profile/run)
        (read-cache-paths/run)
        (read-ignore-paths/run)
        (add-builder-env/run)
        (add-runner-env/run)
        (create-working-directories/run)
        (transfer-assemble-script/run)
        (restore-cache/run)

        ;; --- Run build script
        (collect-sources/run)
        (inject-sources/run)
        (build-script/run)

        ;; --- Run assemble script
        (transfer-artifacts/run)
        (assemble-script/run)

        ;; --- Generate outputs (image, artifacts, cache)
        (commit/run)
        (write-artifacts/run)
        (create-cache/run))
      (cleanup/run)))
