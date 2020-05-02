(ns into.build
  (:require [into.build.flow :as flow]
            [into.utils
             [data :as data]
             [task :as task]]
            [into.constants :as constants]
            [clojure.string :as string]))

;; ## CLI Options

(def ^:private cli-options
  [["-t" "--tag name[:tag]" "Output image name and optionally tag"
    :id :target-image
    :parse-fn (fn [^String value]
                (if-not (string/blank? value)
                  (let [index (.lastIndexOf value ":")]
                    (if (neg? index)
                      (str value ":latest")
                      value))
                  value))
    :validate [#(not (string/blank? %)) "Cannot be blank."]]
   [nil "--write-artifacts path" "Write artifacts to the given path"
    :id :target-path
    :validate [#(not (string/blank? %)) "Cannot be blank."]]
   ["-p" "--profile profile" "Build profile to activate"
    :id :profile
    :default "default"]
   [nil "--ci type" "Run in CI mode, allowed values: 'github-actions'."
    :id :ci-type
    :validate [#{"github-actions"} "Unsupported CI type."]]
   [nil "--cache path" "Use and create the specified cache file for incremental builds."
    :id :cache-file]])

;; ## Spec

(defn- build-spec
  [{:keys [target-image target-path profile cache-file ci-type]}
   [builder-image source-path]]
  (cond-> {:builder-image (data/->image builder-image)
           :source-path   (or source-path ".")}
    target-image (assoc :target-image (data/->image target-image))
    target-path  (assoc :target-path target-path)
    profile      (assoc :profile profile)
    ci-type      (assoc :ci-type ci-type)
    cache-file   (assoc :cache-spec
                        {:cache-from cache-file
                         :cache-to   cache-file})))

;; ## Run

(defn- run-build
  [{:keys [options arguments client]}]
  (-> (flow/run
        {:client           client
         :spec             (build-spec options arguments)
         :well-known-paths constants/well-known-paths})
      (dissoc :client)))

;; ## Tasks

(def run
  (task/make
   {:usage   "into build <options> <builder> [<path>]"
    :cli     cli-options
    :docker? true
    :run     #(run-build %)}))
