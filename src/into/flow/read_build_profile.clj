(ns into.flow.read-build-profile
  (:require [into.docker :as docker]
            [into.utils
             [data :as data]]
            [clojure.tools.logging :as log]
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

(defn- log-build-profile
  [data env]
  (->> env
       (map #(str "[into]   " %))
       (string/join "\n")
       (log/debugf "[into] Build profile [%s]:%n%s"
                   (get-in data [:spec :profile])))
  env)

(defn run
  [data]
  (or (some->> (read-profile-env! data)
               (log-build-profile data)
               (assoc-in data [:instances :builder :image :env]))
      data))
