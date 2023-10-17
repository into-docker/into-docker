(defproject into "1.1.7-SNAPSHOT"
  :description "Never write another Dockerfile."
  :url "https://github.com/into-docker/into-docker"
  :license {:name "MIT"
            :url "https://choosealicense.com/licenses/mit"
            :year 2020
            :key "mit"
            :comment "MIT License"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.0.219"]
                 [org.clojure/tools.logging "1.2.4"]

                 ;; components
                 [lispyclouds/clj-docker-client "1.0.3"]
                 [unixsocket-http "1.0.14"]
                 [com.squareup.okhttp3/okhttp "4.12.0"]
                 [com.squareup.okhttp3/okhttp-tls "4.12.0"]

                 ;; utilities
                 [org.apache.commons/commons-compress "1.24.0"]
                 [commons-lang "2.6"]
                 [potemkin "0.4.6"]

                 ;; logging
                 [jansi-clj "1.0.1"]
                 [ch.qos.logback/logback-classic "1.4.11"]

                 ;; explicit dependencies for GraalVM compatibility
                 [com.fasterxml.jackson.core/jackson-core "2.15.3"]
                 [com.fasterxml.jackson.core/jackson-databind "2.15.3"]

                 ;; cleanup dependency chain
                 [riddley "0.2.0"]
                 [org.jetbrains.kotlin/kotlin-stdlib-common "1.9.10"]
                 [org.jetbrains.kotlin/kotlin-stdlib-jdk8 "1.9.10"]]
  :exclusions [org.clojure/clojure]
  :java-source-paths ["src"]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "1.1.1"]
                             [com.gfredericks/test.chuck "0.2.14"]]
              :global-vars {*warn-on-reflection* true}}
             :kaocha
             {:dependencies [[lambdaisland/kaocha "1.87.1366"
                              :exclusions [org.clojure/spec.alpha]]
                             [lambdaisland/kaocha-cloverage "1.1.89"]
                             [org.clojure/java.classpath "1.0.0"]]}
             :uberjar
             {:global-vars {*assert* false}
              :dependencies [[com.github.clj-easy/graal-build-time "1.0.5"]]
              :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                         "-Dclojure.spec.skip-macros=true"]
              :main into.main
              :aot :all}}
  :cljfmt {:indents {prop/for-all [[:block 1]]
                     defcomponent [[:block 2] [:inner 1]]}}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}
  :pedantic? :abort)
