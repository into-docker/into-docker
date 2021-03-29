(ns into.build.add-shared-volume-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [into.build.spec :as spec]
            [into.build.add-shared-volume :as add-shared-volume]))

(defspec t-add-shared-volume (times 20)
  (prop/for-all
    [spec          (gen/let [n    (s/gen ::spec/target-image-name)
                             spec (s/gen ::spec/spec)]
                     (assoc spec :use-volumes? true, :target-image-name n))
     builder-image (s/gen ::spec/builder-image)
     runner-image  (s/gen ::spec/runner-image)]
    (let [{:keys [builder-image runner-image]}
          (add-shared-volume/run
            {:spec          spec
             :builder-image builder-image
             :runner-image  runner-image})
          builder-volumes (:volumes builder-image)
          runner-volumes (:volumes runner-image)]
      (and (contains? builder-volumes "/tmp/artifacts")
           (contains? runner-volumes "/tmp/artifacts")
           (= builder-volumes runner-volumes)))))

(defspec t-add-shared-volume-when-deactivated (times 20)
  (prop/for-all
    [spec          (gen/let [spec (s/gen ::spec/spec)]
                     (gen/elements
                       [(assoc spec :use-volumes? false)
                        (dissoc spec :target-image-name)]))
     builder-image (s/gen ::spec/builder-image)
     runner-image  (s/gen ::spec/runner-image)]
    (let [{:keys [builder-image runner-image]}
          (add-shared-volume/run
            {:spec          spec
             :builder-image builder-image
             :runner-image  runner-image})
          builder-volumes (:volumes builder-image)
          runner-volumes (:volumes runner-image)]
      (not
        (or (contains? builder-volumes "/tmp/artifacts")
            (contains? runner-volumes "/tmp/artifacts"))))))
