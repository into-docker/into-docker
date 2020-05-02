(ns into.build
  (:require [into.build.flow :as flow]
            [into.utils
             [data :as data]
             [task :as task]]
            [into.constants :as constants]
            [clojure.string :as string]))

;; ## CLI Options

(def ^:private cli-options-base
  [["-p" "--profile profile" "Build profile to activate"
    :id :profile
    :default "default"]
   [nil "--ci type" "Run in CI mode, allowed values: 'github-actions'."
    :id :ci-type
    :validate [#{"github-actions"} "Unsupported CI type."]]
   [nil "--cache path" "Use and create the specified cache file for incremental builds."
    :id :cache-file]])

(def ^:private cli-options-build
  (concat
   [["-t" "--tag name[:tag]" "Output image name and optionally tag"
     :id :target
     :parse-fn (fn [^String value]
                 (if-not (string/blank? value)
                   (let [index (.lastIndexOf value ":")]
                     (if (neg? index)
                       (str value ":latest")
                       value))
                   value))
     :validate [#(not (string/blank? %)) "Cannot be blank."]]]
   cli-options-base))

(def ^:private cli-options-build-artifacts
  (concat
   [["-o" "--output path" "Path to write artifacts to"
     :id :output-path
     :validate [#(not (string/blank? %)) "Cannot be blank."]]]
   cli-options-base))

;; ## Spec

(defn- attach-spec-options
  [spec {:keys [profile cache-file ci-type]} [builder-image source-path]]
  (cond-> spec
    :always    (assoc :builder-image (data/->image builder-image)
                      :source-path   (or source-path "."))
    profile    (assoc :profile profile)
    ci-type    (assoc :ci-type ci-type)
    cache-file (assoc :cache-spec
                      {:cache-from cache-file
                       :cache-to   cache-file})))

(defn- build-spec
  [{:keys [target] :as options} args]
  (-> {:target-image (data/->image target)}
      (attach-spec-options options args)))

(defn- build-artifacts-spec
  [{:keys [output-path] :as options} args]
  (-> {:target-path output-path}
      (attach-spec-options options args)))

;; ## Run

(defn- create-builder
  [spec-fn]
  (fn [{:keys [options arguments client]}]
    (-> (flow/run
         {:client           client
          :spec             (spec-fn options arguments)
          :well-known-paths constants/well-known-paths})
        (dissoc :client))))

;; ## Tasks

(def build
  (task/make
   {:usage   "into build -t <name:tag> <builder> [<path>]"
    :cli     cli-options-build
    :needs   [:target]
    :docker? true
    :run     (create-builder build-spec)}))

(def build-artifacts
  (task/make
   {:usage   "into build-artifacts -o <output-path> <builder> [<path>]"
    :cli     cli-options-build-artifacts
    :needs   [:output-path]
    :docker? true
    :run     (create-builder build-artifacts-spec)}))
