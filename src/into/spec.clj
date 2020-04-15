(ns into.spec
  (:require [into.docker :as docker]
            [into.flow core]
            [clojure.spec.alpha :as s]))

;; ## Generic

(s/def :into/image
  (s/keys :req-un [:into/name
                   :into/tag
                   :into/full-name]
          :opt-un [:into/hash]))

(s/def :into/name string?)
(s/def :into/tag string?)
(s/def :into/full-name string?)
(s/def :into/hash string?)
(s/def :into/path string?)
(s/def :into/file #(instance? java.io.File %))
(s/def :into/error #(instance? Exception %))
(s/def :into/cleanup-error :into/error)
(s/def :into/container some?)
(s/def :into/interrupted? boolean?)
(s/def :into/paths (s/coll-of :into/path))

;; ## Flow

(s/def :into/flow
  (s/keys :req-un [:into/spec
                   :into/well-known-paths
                   :into/client]
          :opt-un [:into/instances
                   :into/vcs
                   :into/sources
                   :into/cleanup-error
                   :into/error
                   :into/interrupted?]))

;; ## Spec

(s/def :into/spec
  (s/keys :req-un [:into/source-path
                   :into/builder-image
                   :into/target-image]
          :opt-un [:into/runner-image
                   :into/cache-spec]))

(s/def :into/builder-image :into/image)
(s/def :into/target-image :into/image)
(s/def :into/runner-image :into/image)
(s/def :into/source-path :into/path)
(s/def :into/client #(satisfies? docker/DockerClient %))

(s/def :into/cache-spec
  (s/keys :req-un [:into/cache-from
                   :into/cache-to]))
(s/def :into/cache-from :into/path)
(s/def :into/cache-to :into/path)

;; ## VCS

(s/def :into/vcs
  (s/keys :req-un [:into/vcs-revision]))
(s/def :into/vcs-revision string?)

;; ## Paths

(s/def :into/well-known-paths
  (s/keys :req-un [:into/source-directory
                   :into/artifact-directory
                   :into/working-directory
                   :into/build-script
                   :into/assemble-script
                   :into/ignore-file]))

(s/def :into/source-directory   :into/path)
(s/def :into/artifact-directory :into/path)
(s/def :into/working-directory :into/path)
(s/def :into/build-script       :into/path)
(s/def :into/assemble-script    :into/path)
(s/def :into/ignore-file        :into/path)

;; ## Image Instance

(s/def :into/instances
  (s/keys :opt-un [:into/builder
                   :into/runner]))

(s/def :into/instance
  (s/keys :req-un [:into/image
                   :into/labels
                   :into/cmd]
          :opt-un [:into/container]))

(s/def :into/builder :into/instance)
(s/def :into/runner  :into/instance)
(s/def :into/labels  (s/map-of keyword? string?))
(s/def :into/cmd     (s/nilable (s/coll-of string?)))

;; ## Sources

(s/def :into/sources
  (s/coll-of :into/source))

(s/def :into/source
  (s/keys :req-un [:into/file
                   :into/path]))

;; ## Cache

(s/def :into/cache
  (s/keys :req-un [:into/paths]))

;; ## Function Specs

(s/fdef into.flow.core/run-step
  :args (s/cat :next-fn fn? :data :into/flow)
  :ret  :into/flow)
