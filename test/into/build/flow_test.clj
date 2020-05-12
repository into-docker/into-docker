(ns into.build.flow-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.constants :as constants]
            [into.test.docker :as docker]
            [into.test.files :refer [with-temp-dir]]
            [into.build.spec :as spec]
            [into.build.flow :as flow]))

;; ## "Scripts"

(defn build-script
  "Creates the given files in the artifact directory."
  [artifacts]
  (fn [this]
    (doseq [[filename contents] artifacts]
      (docker/add-file
        this
        (str (constants/path-for :artifact-directory) "/" filename)
        contents))
    (docker/as-exec-result this "Build was successful.")))

(defn run-script
  "Move stuff from the artifact directory to '/dist/'."
  []
  (fn [this]
    (doseq [[path contents] @(:filesystem this)
            :let [_ (prn path)]
            :when (.startsWith ^String path "/tmp/artifacts/")]
      (docker/add-file
        this
        (str "/dist/" (subs path 15))
        contents))
    (docker/as-exec-result this "Assemble was successful.")))

;; ## Containers

(defn- create-builder-container
  [builder-name artifacts]
  (-> (docker/container builder-name)
      (docker/add-file
        (constants/path-for :build-script)
        `(build-script ~artifacts))
      (docker/add-file
        (constants/path-for :assemble-script)
        `(run-script))))

(defn- create-runner-container
  [runner-name]
  (docker/container runner-name))

(defn- create-client
  [builder-name artifacts]
  (let [runner-name (str builder-name "-run")
        builder-container (create-builder-container builder-name artifacts)
        runner-container (create-runner-container runner-name)
        builder-image-labels {constants/runner-image-label runner-name
                              constants/builder-user-label "builder"}]
    {:client
     (-> (docker/client)
         (docker/add-container builder-name builder-container)
         (docker/add-container runner-name runner-container)
         (docker/add-image builder-name {:Config {:Labels builder-image-labels}})
         (docker/add-image runner-name {}))
     :builder-container builder-container
     :runner-container runner-container}))

;; ## Helpers

(defn- has-target?
  [{:keys [target]} target-name]
  (or (= (:full-name target) target-name)
      (= (:name target) target-name)))

(defn- has-artifacts?
  [{:keys [filesystem]} artifacts]
  (let [artifact-files (->> (keys filesystem)
                            (filter #(.startsWith ^String % "/dist/"))
                            (map #(subs % 6)))]
    (or (= (set artifact-files) (set (keys artifacts)))
        (prn 'XX artifact-files (keys artifacts)))))

;; ## Tests

(defspec t-build-flow-with-target-image (times 20)
  (prop/for-all
    [builder-name (s/gen ::spec/builder-image-name)
     target-name  (s/gen ::spec/target-image-name)
     artifacts    (gen/map (s/gen ::spec/path) gen/string-ascii)]
    (with-temp-dir [target []]
      (let [{:keys [client runner-container]}
            (create-client builder-name artifacts)

            result
            (-> {:spec   {:builder-image-name builder-name
                          :target-image-name  target-name
                          :profile            "default"
                          :source-path        (.getCanonicalPath target)}
                 :client client}
                (flow/run))

            target-image
            @(:committed-container runner-container)]
        (and (not (:error result))
             (has-target? target-image target-name)
             (has-artifacts? target-image artifacts))))))
