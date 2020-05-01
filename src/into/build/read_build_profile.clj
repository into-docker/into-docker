(ns into.build.read-build-profile
  (:require [into.docker :as docker]
            [into.utils
             [log :as log]
             [data :as data]]
            [clojure.string :as string]
            [clojure.java.io :as io]))

(defn- read-profile-env!
  [{:keys [client] :as data}]
  (when-let [profile (get-in data [:spec :profile])]
    (let [path (.getPath
                (io/file (data/path-for data :profile-directory)
                         profile))
          builder (data/instance-container data :builder)
          ^bytes contents (docker/read-container-file! client builder path)]
      (-> contents
          (String. "UTF-8")
          (string/split #"[\n\r]+")
          (->> (map string/trim)
               (remove #(string/starts-with? % "#"))
               (remove string/blank?))))))

(defn- assoc-build-profile
  [data env]
  (if-let [profile (get-in data [:spec :profile])]
    (cond (seq env)
          (do
            (log/debug data "Build profile [%s]:" profile)
            (doseq [e env]
              (log/debug data "  %s" e))
            (assoc-in data [:instances :builder :image :env] env))

          (= profile "default")
          (log/debug data "Build profile [%s] is empty." profile)

          :else
          (assoc data
                 :error
                 (IllegalStateException.
                   (format "Build profile [%s] is empty." profile))))
    data))

(defn run
  [data]
  (or (some->> (read-profile-env! data)
               (assoc-build-profile data))
      data))
