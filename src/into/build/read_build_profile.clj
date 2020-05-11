(ns into.build.read-build-profile
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [flow :as flow]
             [log :as log]]
            [clojure.string :as string]
            [clojure.java.io :as io]))

;; ## Read the Profile

(defn- profile-path
  [profile]
  (-> (constants/path-for :profile-directory)
      (io/file profile)
      (.getPath)))

(defn- split-env
  [^bytes contents]
  (-> contents
      (String. "UTF-8")
      (string/split #"[\n\r]+")
      (->> (map string/trim)
           (remove #(string/starts-with? % "#"))
           (remove string/blank?))))

(defn- read-profile-env!
  [{:keys [builder-container spec]}]
  (->> (:profile spec)
       (profile-path)
       (docker/read-file builder-container)
       (split-env)))

;; ## Update the builder env

(defn- assoc-build-profile
  [data env]
  (let [profile (get-in data [:spec :profile])]
    (cond (seq env)
          (do
            (log/debug "Build profile [%s]:" profile)
            (doseq [e env]
              (log/debug "|   %s" e))
            (update data :builder-env concat env))

          (= profile "default")
          (do
            (log/debug "Build profile [%s] is empty." profile)
            (update data :builder-env concat env))

          :else
          (flow/fail data (format "Build profile [%s] is empty." profile)))))

;; ## Flow

(defn run
  [data]
  (->> (read-profile-env! data)
       (assoc-build-profile data)))
