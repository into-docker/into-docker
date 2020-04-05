(defproject into "1.0.0-SNAPSHOT"
  :description "Never write another Dockerfile."
  :url "https://github.com/into-docker/into-docker"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2020
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.0.0"]
                 [lispyclouds/clj-docker-client "0.5.3"]
                 [org.apache.commons/commons-compress "1.20"]
                 [commons-io "2.6"]
                 [commons-lang "2.6"]
                 [peripheral "0.5.3"]
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
