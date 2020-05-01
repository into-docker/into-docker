(defproject into "1.0.0-RC4"
  :description "Never write another Dockerfile."
  :url "https://github.com/into-docker/into-docker"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2020
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.1.0"]
                 [lispyclouds/clj-docker-client "1.0.0-RC2"]
                 [org.apache.commons/commons-compress "1.20"]
                 [commons-lang "2.6"]
                 [peripheral "0.5.4"]
                 [jansi-clj "0.1.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]

                 ;; cleanup dependency chain
                 [org.jetbrains.kotlin/kotlin-stdlib-common "1.3.72"]]
  :exclusions [org.clojure/clojure]
  :java-source-paths ["src"]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "1.0.0"]
                             [com.gfredericks/test.chuck "0.2.10"]]
              :plugins [[lein-cljfmt "0.6.7"]]
              :global-vars {*warn-on-reflection* true}}
             :kaocha
             {:dependencies [[lambdaisland/kaocha "1.0-612"
                              :exclusions [org.clojure/spec.alpha]]]}
             :uberjar
             {:global-vars {*assert* false}
              :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                         "-Dclojure.spec.skip-macros=true"]
              :main into.main
              :aot :all}}
  :cljfmt {:indents {prop/for-all [[:block 1]]
                     defcomponent [[:block 2] [:inner 1]]}}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}
  :pedantic? :abort)
