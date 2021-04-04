(ns into.build.inject-sources-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck :refer [times]]
            [into.build.inject-sources :as inject-sources]
            [into.constants :as constants]
            [into.docker.mock :as mock]
            [into.test.files :refer [with-temp-dir]]
            [into.test.generators :refer [gen-unique-paths]]))

(defspec t-inject-sources (times 20)
  (let [builder-src (constants/path-for :source-directory)]
    (prop/for-all
      [sources (gen/not-empty (gen-unique-paths))]
      (with-temp-dir [source-path sources]
        (let [builder (mock/running-container)
              data {:spec {:source-path source-path}
                    :source-paths sources
                    :builder-container builder}
              data' (inject-sources/run data)]
          (and (= data data')
               (= (set (map #(str builder-src "/" %) sources))
                  (mock/list-files builder builder-src))))))))
