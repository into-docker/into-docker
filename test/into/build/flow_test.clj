(ns into.build.flow-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging.test :refer [with-log]]
            [into.docker.mock :as docker]
            [into.test.files :refer [with-temp-dir]]
            [into.build.spec :as spec]
            [into.build.flow :as flow]))

;; ## Note
;;
;; This file does not use `into.constants` since we want the test to fail if
;; there is an incompatible change in the constants.

;; ## "Scripts"

(defn build-script
  "Creates the given files in the artifact directory."
  [artifacts]
  (fn [{:keys [container env]}]
    (let [artifact-dir (get env "INTO_ARTIFACT_DIR")]
      (doseq [[filename contents] artifacts]
        (docker/add-file container (str artifact-dir "/" filename) contents))
      (docker/as-exec-result container "Build was successful."))))

(defn run-script
  "Move stuff from the artifact directory to '/dist/'."
  []
  (fn [{:keys [container env]}]
    (let [artifact-dir (get env "INTO_ARTIFACT_DIR")]
      (doseq [path (docker/list-contents container artifact-dir)
              :let [target (str "/dist/" path)]]
        (docker/move-file container path target)))
    (docker/as-exec-result container "Assemble was successful.")))

;; ## Containers

(defn- create-builder-container
  [builder-name artifacts]
  (-> (docker/container builder-name)
      (docker/add-file "/into/bin/build"    `(build-script ~artifacts))
      (docker/add-file "/into/bin/assemble" `(run-script))))

(defn- create-runner-container
  [runner-name]
  (docker/container runner-name))

(defn- create-client
  [builder-name artifacts]
  (let [runner-name          (str builder-name "-run")
        builder-container    (create-builder-container builder-name artifacts)
        runner-container     (create-runner-container runner-name)
        builder-image-labels {:org.into-docker.runner-image runner-name
                              :org.into-docker.builder-user "builder"}]
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
  [{:keys [fs]} artifacts]
  (let [artifact-files (docker/list-contents fs "/dist")]
    (= (set artifact-files)
       (set (keys artifacts)))))

;; ## Tests

(defspec t-build-flow-with-target-image (times 20)
  (prop/for-all
    [builder-name (s/gen ::spec/builder-image-name)
     target-name  (s/gen ::spec/target-image-name)
     artifacts    (gen/map (s/gen ::spec/path) gen/string-ascii)]
    (with-log
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
              @(:commit runner-container)]
          (and (not (:error result))
               (has-target? target-image target-name)
               (has-artifacts? target-image artifacts)))))))
