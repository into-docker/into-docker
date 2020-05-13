(ns into.build.flow-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging.test :refer [with-log]]
            [clojure.java.io :as io]
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

(defn- has-target-image?
  [{:keys [target-image commit]}]
  (and (some? commit)
       (= (:full-name commit) (:full-name target-image))))

(defn- has-committed-artifacts?
  [{:keys [commit]} artifacts]
  (when commit
    (let [artifact-files (docker/list-contents (:fs commit) "/dist")]
      (= (set artifact-files)
         (set (keys artifacts))))))

(defn- has-written-artifacts?
  [{:keys [spec]} artifacts]
  (let [{:keys [artifact-path]} spec
        artifact-files (->> (io/file artifact-path)
                            (file-seq)
                            (filter #(.isFile ^java.io.File %))
                            (map #(.getCanonicalPath ^java.io.File %))
                            (map #(subs % (inc (count artifact-path)))))]
    (= (set artifact-files)
       (set (keys artifacts)))))

(defn- run-build-flow
  [{:keys [^java.io.File source-path artifacts]}
   {:keys [builder-image-name artifact-path] :as spec}]
  (let [{:keys [client runner-container]}
        (create-client builder-image-name artifacts)]
    (merge
      (-> {:spec   (cond-> spec
                     artifact-path
                     (assoc :artifact-path
                            (.getCanonicalPath (io/file source-path artifact-path)))
                     :always
                     (assoc :source-path
                            (.getCanonicalPath source-path)))
           :client client}
          (flow/run))
      {:commit @(:commit runner-container)})))

;; ## Tests

(defspec t-build-flow-with-target-image (times 10)
  (prop/for-all
    [spec      (gen/hash-map
                 :builder-image-name (s/gen ::spec/builder-image-name)
                 :target-image-name  (s/gen ::spec/target-image-name)
                 :profile            (gen/return "default"))
     artifacts (gen/map (s/gen ::spec/path) gen/string-ascii)]
    (with-log
      (with-temp-dir [source-path []]
        (let [result (run-build-flow
                      {:source-path source-path
                       :artifacts   artifacts}
                      spec)]
          (and (not (:error result))
               (has-target-image? result)
               (has-committed-artifacts? result artifacts)))))))

(defspec t-build-flow-with-artifact-path (times 10)
  (prop/for-all
    [spec      (gen/hash-map
                 :builder-image-name (s/gen ::spec/builder-image-name)
                 :artifact-path      (s/gen ::spec/path)
                 :profile            (gen/return "default"))
     artifacts (gen/map (s/gen ::spec/path) gen/string-ascii)]
    (with-log
      (with-temp-dir [source-path []]
        (let [result (run-build-flow
                      {:source-path source-path
                       :artifacts   artifacts}
                      spec)]
          (and (not (:error result))
               (not (:commit result))
               (has-written-artifacts? result artifacts)))))))
