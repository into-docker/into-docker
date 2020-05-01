(ns into.utils.log
  (:require [clojure.tools.logging :as log]
            [jansi-clj.core :as jansi]))

(defmacro log*
  [log-fn data color fmt & args]
  (let [sym (gensym "data")]
    `(let [~sym ~data]
       (~log-fn ~(jansi/fg color (str "[into] " fmt))
                ~@args)
       ~sym)))

(defmacro info
  [data fmt & spec-keys]
  `(log* log/infof ~data :default ~fmt ~@spec-keys))

(defmacro debug
  [data fmt & spec-keys]
  `(log* log/debugf ~data :default ~fmt ~@spec-keys))

(defmacro trace
  [data fmt & spec-keys]
  `(log* log/debugf ~data :default ~fmt ~@spec-keys))

(defmacro emph
  [data fmt & spec-keys]
  `(log* log/infof ~data :yellow ~fmt ~@spec-keys))

(defmacro warn
  [data fmt & spec-keys]
  `(log* log/warnf ~data :red ~fmt ~@spec-keys))

(defmacro success
  [data fmt & spec-keys]
  `(log* log/infof ~data :green ~fmt ~@spec-keys))

(defmacro report-error
  [error message]
  `(let [^Exception error# ~error]
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
     (when (or error# cleanup-error#)
       (log/info "[into] See the full stacktrace using '-v'."))
     data#))
