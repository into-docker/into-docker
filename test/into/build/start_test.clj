(ns into.build.start-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging.test :refer [with-log]]
            [into.build.spec :as spec]
            [into.build.start :as start]
            [into.docker.mock :as mock]))

;; ## Helpers

(defn- make-client
  [builder-image runner-image]
  (let [builder (mock/container)
        runner  (mock/container)
        client  (cond-> (mock/client)
                  builder-image (mock/add-container
                                  (:full-name builder-image)
                                  builder)
                  runner-image  (mock/add-container
                                  (:full-name runner-image)
                                  runner))]
    {:client  client
     :builder builder
     :runner  runner}))

(defn- maybe
  [g]
  (gen/one-of [g (gen/return nil)]))

;; ## Tests

(defspec t-start (times 20)
  (prop/for-all
    [spec          (s/gen ::spec/spec)
     builder-image (s/gen ::spec/image)
     runner-image  (maybe (s/gen ::spec/image))]
    (with-log
      (let [{:keys [builder runner client]}
            (make-client builder-image runner-image)
            data {:spec   spec
                  :client client
                  :builder-image builder-image
                  :runner-image  runner-image}
            data' (start/run data)]
        (and (not (:error data'))
             (= [:run] (mock/events builder))
             (or (not runner-image)
                 (= [:run] (mock/events runner))))))))
