(ns into.build
  (:require [into.build.flow :as flow]
            [into.utils
             [data :as data]
             [task :as task]]
            [clojure.string :as string]))

;; ## Constants

(let [working-directory "/tmp"]
  (def ^:private +well-known-paths+
    {:source-directory   (str working-directory "/src")
     :artifact-directory (str working-directory "/artifacts")
     :cache-directory    (str working-directory "/cache")
     :working-directory  working-directory

     :build-script       "/into/bin/build"
     :assemble-script    "/into/bin/assemble"

     :profile-directory  "/into/profiles"
     :cache-file         "/into/cache"
     :ignore-file        "/into/ignore"}))

;; ## CLI Options

(def ^:private cli-options
  [["-t" "--tag name[:tag]" "Output image name and optionally tag"
    :id :target
    :parse-fn (fn [^String value]
                (if-not (string/blank? value)
                  (let [index (.lastIndexOf value ":")]
                    (if (neg? index)
                      (str value ":latest")
                      value))
                  value))
    :validate [#(not (string/blank? %)) "Cannot be blank."]]
   ["-p" "--profile profile" "Build profile to activate"
    :id :profile
    :default "default"]
   [nil "--cache path" "Use and create the specified cache file for incremental builds."
    :id :cache-file]
   ["-h" "--help" "Show help"]])

;; ## Subtask

(defn- build-flow-spec
  [{:keys [target profile cache-file]}
   [builder-image source-path]]
  (cond->
   {:builder-image (data/->image builder-image)
    :target-image  (data/->image target)
    :source-path   (or source-path ".")
    :profile       profile}

    cache-file
    (assoc :cache-spec
           {:cache-from cache-file
            :cache-to   cache-file})))

(defn- run-build
  [{:keys [options arguments client show-help show-error]}]
  (cond (empty? arguments)
        (show-help)

        (not (:target options))
        (show-error "Please supply a target name and tag using '-t'")

        :else
        (-> (flow/run
              {:client           client
               :spec             (build-flow-spec options arguments)
               :well-known-paths +well-known-paths+})
            (dissoc :client))))

(def subtask
  (task/make
    {:usage   "into build -t <name:tag> <builder> [<path>]"
     :cli     cli-options
     :docker? true
     :run     run-build}))
