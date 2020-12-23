(defproject into "1.0.1-SNAPSHOT"
  :description "Never write another Dockerfile."
  :url "https://github.com/into-docker/into-docker"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2020
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.1.0"]

                 ;; components
                 [lispyclouds/clj-docker-client "1.0.2"]

                 ;; utilities
                 [org.apache.commons/commons-compress "1.20"]
                 [commons-lang "2.6"]
                 [potemkin "0.4.5"]

                 ;; logging
                 [jansi-clj "0.1.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]

                 ;; cleanup dependency chain
                 [riddley "0.2.0"]
                 [org.jetbrains.kotlin/kotlin-stdlib-common "1.4.21"]]
  :exclusions [org.clojure/clojure]
  :java-source-paths ["src"]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "1.1.0"]
                             [com.gfredericks/test.chuck "0.2.10"]]
              :plugins [[lein-cljfmt "0.6.7"]]
              :global-vars {*warn-on-reflection* true}}
             :kaocha
             {:dependencies [[lambdaisland/kaocha "1.0.732"
                              :exclusions [org.clojure/spec.alpha]]
                             [lambdaisland/kaocha-cloverage "1.0.75"]
                             [org.clojure/java.classpath "1.0.0"]]}
             :ci
             [:kaocha
              {:dependencies [[lambdaisland/kaocha-cloverage "1.0.75"]
                              [org.clojure/java.classpath "1.0.0"]]
               :global-vars {*warn-on-reflection* false}}]
             :uberjar
             {:global-vars {*assert* false}
              :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                         "-Dclojure.spec.skip-macros=true"]
              :main into.main
              :aot :all}}
  :cljfmt {:indents {prop/for-all [[:block 1]]
                     defcomponent [[:block 2] [:inner 1]]}}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "ci"     ["with-profile" "+ci" "run" "-m" "kaocha.runner"
                      "--reporter" "documentation"
                      "--plugin"   "cloverage"
                      "--codecov"
                      "--no-cov-html"]}
  :pedantic? :abort)
