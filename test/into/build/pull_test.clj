(ns into.build.pull-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.tools.logging.test :refer [with-log]]
            [into.test.generators :refer [gen-spec-and-images]]
            [into.build.pull :as pull]
            [into.docker.mock :as mock]))

;; ## Helper

(defn- error-not-found?
  [{:keys [^Exception error]} {:keys [full-name]}]
  (some-> error
          (.getMessage)
          (= (str "Image not found: " full-name))))

;; ## Tests

(defspec t-pull (times 20)
  (prop/for-all
    [{:keys [spec builder runner user]} (gen-spec-and-images)]
    (with-log
      (let [{:keys [builder-image runner-image error]}
            (pull/run
              {:spec spec
               :client (-> (mock/client)
                           (mock/add-image builder)
                           (cond-> runner (mock/add-image runner)))})]
        (and (not error)
             (= (:full-name builder) (:full-name builder-image))
             (= user (:user builder-image))
             (= (:full-name runner) (:full-name runner-image)))))))

(defspec t-pull-fails-if-builder-image-missing (times 20)
  (prop/for-all
    [{:keys [spec builder]} (gen-spec-and-images)]
    (with-log
      (let [data (pull/run {:spec spec, :client (mock/client)})]
        (error-not-found? data builder)))))

(defspec t-pull-fails-if-runner-image-missing (times 20)
  (prop/for-all
    [{:keys [spec builder runner]} (->> (gen-spec-and-images)
                                        (gen/such-that :runner))]
    (with-log
      (let [data (pull/run {:spec   spec
                            :client (-> (mock/client)
                                        (mock/add-image builder))})]
        (error-not-found? data runner)))))
