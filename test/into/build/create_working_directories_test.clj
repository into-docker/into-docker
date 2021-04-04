(ns into.build.create-working-directories-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging.test :refer [with-log]]
            [into.build.create-working-directories :as create-working-directories]
            [into.docker.mock :as mock]))

(deftest t-create-working-directories
  (with-log
    (let [builder (mock/running-container)
          runner (mock/running-container)
          data {:builder-container builder
                :runner-container runner}
          data' (create-working-directories/run data)]
      (is (not (:error data')))
      (is (= [:run
              [:mkdir "-p" "/tmp/src" "/tmp/artifacts" "/tmp/cache"]
              [:chown "-R" "builder" "/tmp/src" "/tmp/artifacts" "/tmp/cache"]]
             (mock/events builder)))
      (is (= [:run
              [:mkdir "-p" "/tmp/artifacts"]]
             (mock/events runner))))))
