(ns into.build.read-buildenv
  (:require [into
             [constants :as constants]
             [flow :as flow]
             [log :as log]]
            [clojure.java.io :as io]
            [clojure.string :as string]))

;; ## Read `.buildenv` file

(defn- read-buildenv-file!
  [{{:keys [source-path]} :spec}]
  (let [file (io/file source-path constants/build-env-file)]
    (when (.isFile file)
      (with-open [in (io/reader file)]
        (->> (line-seq in)
             (map string/trim)
             (remove #(string/starts-with? % "#"))
             (remove string/blank?)
             (doall)
             (seq))))))

(defn- add-builder-env
  [data env value]
  (update data :builder-env (fnil conj []) (str env '= value)))

(defn- fail-if-missing
  [data missing]
  (if (seq missing)
    (->> (format (str "Required environment variables are missing: %s"
                      " (See your source directory's '%s' file.)")
                 (string/join ", " missing)
                 constants/build-env-file)
         (flow/fail data))
    data))

(defn- import-envs!
  [data' envs]
  (loop [data    data'
         envs    envs
         missing []]
    (if (seq envs)
      (let [[env & rst] envs]
        (if-let [value (System/getenv env)]
          (recur (add-builder-env data env value) rst missing)
          (recur data rst (conj missing env))))
      (fail-if-missing data missing))))

;; ## Flow

(defn run
  [data]
  (or (when-let [envs (read-buildenv-file! data)]
        (log/debug "Environment from '%s': %s"
                   constants/build-env-file
                   envs)
        (import-envs! data envs))
      data))
