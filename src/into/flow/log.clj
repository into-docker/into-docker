(ns into.flow.log
  (:require [clojure.tools.logging :as log]
            [jansi-clj.core :as jansi]))

(defmacro log*
  [data color fmt & spec-keys]
  (let [sym (gensym "data")]
    `(let [~sym ~data]
       (log/infof ~(jansi/fg color (str "[into] " fmt))
                  ~@(map
                      (fn [spec-key]
                        `(get-in ~sym [:spec ~spec-key]))
                      spec-keys))
       ~sym)))

(defmacro info
  [data fmt & spec-keys]
  `(log* ~data :default ~fmt ~@spec-keys))

(defmacro emph
  [data fmt & spec-keys]
  `(log* ~data :yellow ~fmt ~@spec-keys))

(defmacro success
  [data fmt & spec-keys]
  `(log* ~data :green ~fmt ~@spec-keys))

(defmacro report-error
  [error message]
  `(let [error# ~error]
     (when error#
       (log/errorf
         ~(jansi/red "[into] " message " [%s] %s")
         (.getSimpleName (class error#))
         (.getMessage error#))
       (log/debugf error# "[into] Stacktrace"))))

(defmacro report-errors
  [data]
  `(let [data# ~data
         {error# :error, cleanup-error# :cleanup-error} data#]
     (report-error cleanup-error# "An error occured during cleanup:")
     (report-error error#         "An error occured:")
     data#))
