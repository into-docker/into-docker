(defproject into "1.1.4-SNAPSHOT"
  :description "Never write another Dockerfile."
  :url "https://github.com/into-docker/into-docker"
  :license {:name "MIT"
            :url "https://choosealicense.com/licenses/mit"
            :year 2020
            :key "mit"
            :comment "MIT License"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.clojure/tools.logging "1.1.0"]

                 ;; components
                 [lispyclouds/clj-docker-client "1.0.3"]
                 [unixsocket-http "1.0.11"]
                 [com.squareup.okhttp3/okhttp "4.9.3"]
                 [com.squareup.okhttp3/okhttp-tls "4.9.3"]

                 ;; utilities
                 [org.apache.commons/commons-compress "1.20"]
                 [commons-lang "2.6"]
                 [potemkin "0.4.5"]

                 ;; logging
                 [jansi-clj "0.1.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]

                 ;; cleanup dependency chain
                 [riddley "0.2.0"]
                 [org.jetbrains.kotlin/kotlin-stdlib-common "1.6.0"]]
  :exclusions [org.clojure/clojure]
  :java-source-paths ["src"]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "1.1.0"]
                             [com.gfredericks/test.chuck "0.2.10"]]
              :global-vars {*warn-on-reflection* true}}
             :kaocha
             {:dependencies [[lambdaisland/kaocha "1.0.829"
                              :exclusions [org.clojure/spec.alpha]]
                             [lambdaisland/kaocha-cloverage "1.0.75"]
                             [org.clojure/java.classpath "1.0.0"]]}
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
