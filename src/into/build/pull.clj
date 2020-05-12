(ns into.build.pull
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [flow :as flow]
             [log :as log]]))

;; ## Steps

(defn- pull-image!
  [{:keys [client] :as data} target-key image-name]
  (log/debug "  Pulling image [%s] ..." image-name)
  (if-let [image (docker/pull-image-record client image-name)]
    (assoc data target-key image)
    (flow/fail data (str "Image not found: " image-name))))

(defn- pull-builder-image!
  [data]
  (->> (get-in data [:spec :builder-image-name])
       (pull-image! data :builder-image)))

(defn- pull-runner-image!
  [data]
  (->> (get-in data [:builder-image
                     :labels
                     constants/runner-image-label])
       (pull-image! data :runner-image)))

(defn- set-builder-user
  [data]
  (if-let [user (get-in data [:builder-image
                              :labels
                              constants/builder-user-label])]
    (assoc-in data [:builder-image :user] user)
    data))

(defn- verify-runner-image
  [data]
  (flow/validate
   data
   [:builder-image :labels constants/runner-image-label]
   "No runner image found in builder image labels."))

;; ## Flow

(defn run
  [data]
  (log/info "Pulling necessary images ...")
  (flow/with-flow-> data
    (pull-builder-image!)
    (set-builder-user)
    (verify-runner-image)
    (pull-runner-image!)))
