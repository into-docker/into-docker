(ns into.build.commit-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging.test :refer [with-log]]
            [into.build.spec :as spec]
            [into.build.commit :as commit]
            [into.docker.mock :as mock]))

(defspec t-commit (times 20)
  (prop/for-all
    [target-image (s/gen ::spec/image)]
    (with-log
      (let [runner-container (mock/container)
            data {:target-image     target-image
                  :runner-container runner-container}
            data' (commit/run data)
            committed @(:commit runner-container)]
        (and (= data' data)
             (= target-image (dissoc committed :fs)))))))

(defspec t-commit-does-nothing-without-target-image (times 5)
  (prop/for-all
    []
    (with-log
      (let [runner-container (mock/container)
            data {:runner-container runner-container}
            data' (commit/run data)
            committed @(:commit runner-container)]
        (and (= data' data) (not committed))))))
