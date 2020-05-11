(ns into.log
  (:require [clojure.tools.logging :as log]
            [jansi-clj.core :as jansi]))

;; ## Log

(defmacro ^:private deflog
  [sym log-fn & [color mode]]
  (let [color (or color :default)]
    `(defmacro ~sym
       ~'[fmt & args]
       (list* '~(symbol (resolve log-fn))
              ~(case mode
                 :stdout `(str "[into] |   " (jansi/fg ~color ~'fmt))
                 :stderr `(str "[into] |   " (jansi/fg-bright ~color ~'fmt))
                 `(jansi/fg ~color (str "[into] " ~'fmt)))
              ~'args))))

(deflog info    log/infof)
(deflog debug   log/debugf)
(deflog trace   log/tracef)
(deflog emph    log/infof :yellow)
(deflog warn    log/warnf :red)
(deflog error   log/errorf :red)
(deflog success log/infof :green)

(deflog stdout  log/infof :default :stdout)
(deflog stderr  log/infof :red :stderr)

;; ## Report

(defn- report-error
  [^Exception e message]
  (when e
    (error "%s%s" message (.getMessage e))
    (log/debugf e "[into] [%s] Stacktrace" (.getSimpleName (class e)))))

(defn report-errors
  [{:keys [cleanup-error error] :as data}]
  (report-error cleanup-error "An error occured during cleanup: ")
  (report-error error         "")
  (when (or error cleanup-error)
    (info "See the full stacktrace using '-v'."))
  data)

(defn report-exec
  [{:keys [stream line]}]
  (case stream
    :stdout (stdout "%s" line)
    :stderr (stderr "%s" line)))

;; ## Helper

(let [prefixes "KMGTPE"
      unit     1024
      unit-log (Math/log unit)]
  (defn as-file-size
    ^String [v]
    (if (< v unit)
      (str v "B")
      (let [exp    (int (min (/ (Math/log v) unit-log) 6))
            value  (/ v (Math/pow unit exp))
            prefix (.charAt prefixes (dec exp))]
        (format "%.1f%siB" value prefix)))))
