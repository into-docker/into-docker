(ns into.build.flow-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging.test :refer [with-log]]
            [clojure.java.io :as io]
            [into.docker.tar :refer [untar-seq]]
            [into.docker.mock :as docker]
            [into.test.generators :refer [gen-unique-paths]]
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
      (doseq [filename artifacts]
        (docker/add-file container (str artifact-dir "/" filename) filename))
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
      (docker/add-file "/into/bin/build"    `(build-script [~@artifacts]))
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

(defn- has-source-paths?
  [{:keys [source-paths]} paths]
  (= (set source-paths) (set paths)))

(defn- has-cache-paths?
  [{:keys [cache-paths]} paths]
  (= (set cache-paths)
     (set (map #(str "/tmp/src/" %) paths))))

(defn- has-created-cache?
  [{:keys [spec cache-paths]}]
  (when-let [cache-to (some-> spec :cache-to (io/file))]
    (and (.isFile cache-to)
         (= (count cache-paths)
            (with-open [in (io/input-stream cache-to)
                        gz (java.util.zip.GZIPInputStream. in)]
              (count (untar-seq gz)))))))

(defn- has-committed-artifacts?
  [{:keys [commit]} artifacts]
  (when commit
    (let [artifact-files (docker/list-contents (:fs commit) "/dist")]
      (= (set artifact-files) (set artifacts)))))

(defn- has-written-artifacts?
  [{:keys [spec]} artifacts]
  (let [{:keys [artifact-path]} spec
        artifact-files (->> (io/file artifact-path)
                            (file-seq)
                            (filter #(.isFile ^java.io.File %))
                            (map #(.getCanonicalPath ^java.io.File %))
                            (map #(subs % (inc (count artifact-path)))))]
    (= (set artifact-files) (set artifacts))))

(defn- run-build-flow
  [{:keys [^java.io.File source-path
           ^java.io.File artifact-path
           ^java.io.File cache-to
           artifacts
           cache-file
           ignore-file]}
   {:keys [builder-image-name] :as spec}]
  (let [{:keys [client builder-container runner-container]}
        (create-client builder-image-name artifacts)]
    (some->> ignore-file (docker/add-file builder-container "/into/ignore"))
    (some->> cache-file (docker/add-file builder-container "/into/cache"))
    (merge
      (-> {:spec   (cond-> spec
                     artifact-path
                     (assoc :artifact-path (.getCanonicalPath artifact-path))
                     cache-to
                     (assoc :cache-to (.getCanonicalPath cache-to))
                     :always
                     (assoc :source-path (.getCanonicalPath source-path)))
           :client client}
          (flow/run))
      {:commit @(:commit runner-container)})))

;; ## Generators

(defn- attach-via-gen
  [gen k k-gen]
  (if k-gen
    (gen/bind
      gen
      (fn [spec]
        (gen/fmap
          #(cond-> spec % (assoc k %))
          (if (instance? clojure.test.check.generators.Generator k-gen)
            k-gen
            (gen/return k-gen)))))
    gen))

(defn maybe-gen
  [gen]
  (gen/one-of [gen (gen/return nil)]))

(defn- gen-spec
  [{:keys [profile
           target-image-name
           ci-type]
    :or {ci-type (maybe-gen (s/gen ::spec/ci-type))
         profile (gen/return "default")}}]
  (-> (gen/hash-map
        :builder-image-name (s/gen ::spec/builder-image-name)
        :profile            profile
        :use-volumes?       (gen/return false))
      (attach-via-gen :target-image-name target-image-name)
      (attach-via-gen :ci-type ci-type)))

;; ## Tests

(defspec t-build-flow-with-target-image (times 10)
  (prop/for-all
    [spec      (gen-spec {:target-image-name (s/gen ::spec/target-image-name)})
     artifacts (gen-unique-paths)
     sources   (gen/not-empty (gen-unique-paths))]
    (with-log
      (with-temp-dir [source-path sources]
        (let [result (run-build-flow
                      {:source-path source-path
                       :artifacts   artifacts}
                      spec)]
          (and (not (:error result))
               (has-source-paths? result sources)
               (has-target-image? result)
               (has-committed-artifacts? result artifacts)))))))

(defspec t-build-flow-with-artifact-path (times 10)
  (prop/for-all
    [spec      (gen-spec {})
     artifacts (gen-unique-paths)
     sources   (gen/not-empty (gen-unique-paths))]
    (with-log
      (with-temp-dir [source-path   sources
                      artifact-path []]
        (let [result (run-build-flow
                       {:source-path   source-path
                        :artifacts     artifacts
                        :artifact-path artifact-path}
                       spec)]
          (and (not (:error result))
               (not (:commit result))
               (has-source-paths? result sources)
               (has-written-artifacts? result artifacts)))))))

(defspec t-build-flow-with-target-image-and-cache (times 10)
  (prop/for-all
    [spec      (gen-spec {:target-image-name (s/gen ::spec/target-image-name)})
     artifacts (gen-unique-paths)
     sources   (gen/not-empty (gen-unique-paths))
     cache-to  (s/gen ::spec/name)]
    (with-log
      (with-temp-dir [source-path sources
                      out-path    []]
        (let [path-to-cache (first sources)
              result (run-build-flow
                       {:source-path source-path
                        :artifacts   artifacts
                        :cache-to    (io/file out-path cache-to)
                        :cache-file  path-to-cache}
                       spec)]
          (and (not (:error result))
               (has-cache-paths? result [path-to-cache])
               (has-created-cache? result)
               (has-target-image? result)
               (has-committed-artifacts? result artifacts)))))))
