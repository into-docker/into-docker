(defproject into "0.1.0-SNAPSHOT"
  :description "Never write another Dockerfile."
  :url "https://github.com/into-docker/into-docker"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2020
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [lispyclouds/clj-docker-client "0.5.3"]
                 [metosin/jsonista "0.2.5"]
                 [org.apache.commons/commons-compress "1.20"]
                 [commons-lang "2.6"]
                 [peripheral "0.5.3"]

                 ;; logging dependencies
                 [org.clojure/tools.logging "1.0.0"]
                 [jansi-clj "0.1.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]

                 ;; cleanup dependency chain
                 [org.jetbrains.kotlin/kotlin-stdlib-common "1.3.71"]]
  :exclusions [org.clojure/clojure]
  :java-source-paths ["src"]
  :profiles {:uberjar {:global-vars
                       {*assert* false}
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.spec.skip-macros=true"]
                       :main into.main
                       :aot :all}}
  :pedantic? :abort)
