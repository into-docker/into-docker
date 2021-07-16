(ns into.build
  (:require [into.build.flow :as flow]
            [into.utils.task :as task]
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
    :id :artifact-path
    :validate [#(not (string/blank? %)) "Cannot be blank."]]
   ["-p" "--profile profile" "Build profile to activate"
    :id :profile
    :default "default"]
   [nil "--ci type" "Run in CI mode, allowed values: 'github-actions'."
    :id :ci-type
    :validate [#{"github-actions"} "Unsupported CI type."]]
   [nil, "--no-volumes" "Do not use shared volumes."
    :id :no-volumes?
    :default false]
   ["-i" "--incremental" "Run an incremental build utilising a Docker volume for caching."
    :id :incremental?
    :default false]
   [nil "--cache path" "Run an incremental build utilising the given cache file."
    :id :cache-file]])

;; ## Spec

(defn- build-spec
  [{:keys [target-image
           artifact-path
           profile
           cache-file
           ci-type
           incremental?
           no-volumes?]}
   [builder-image source-path]]
  (cond-> {:builder-image-name builder-image
           :source-path        (or source-path ".")
           :use-cache-volume?  (and incremental? (not cache-file))
           :use-volumes?       (not no-volumes?)}
    target-image  (assoc :target-image-name target-image)
    artifact-path (assoc :artifact-path artifact-path)
    profile       (assoc :profile profile)
    ci-type       (assoc :ci-type ci-type)
    cache-file    (merge {:cache-from cache-file, :cache-to cache-file})))

;; ## Run

(defn- run-build
  [{:keys [options arguments client]}]
  (-> (flow/run
       {:client client
        :spec   (build-spec options arguments)})
      (dissoc :client)))

;; ## Tasks

(def run
  (task/make
   {:usage   "into build <options> <builder> [<path>]"
    :cli     cli-options
    :docker? true
    :run     #(run-build %)}))
