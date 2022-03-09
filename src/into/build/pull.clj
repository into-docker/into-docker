(ns into.build.pull
  (:require [into
             [constants :as constants]
             [docker :as docker]
             [flow :as flow]
             [log :as log]]))

;; ## Steps

(defn- pull-image!
  [{:keys [client] :as data} target-key platform image-name]
  (log/debug "  Pulling image [%s]%s ..."
             image-name
             (if-let [p (or platform (:platform client))]
               (format " for platform [%s]" p)
               ""))

  (if-let [image (-> client
                     (cond-> platform (docker/with-platform platform))
                     (docker/pull-image-record image-name))]
    (assoc data target-key image)
    (flow/fail data (str "Image not found: " image-name))))

(defn- pull-builder-image!
  [{:keys [spec] :as data}]
  (let [{:keys [builder-image-name]} spec]
    (pull-image! data :builder-image nil builder-image-name)))

(defn- pull-runner-image!
  [{:keys [spec] :as data}]
  (let [{:keys [target-image-name platform]} spec]
    (if target-image-name
      (->> (get-in data [:builder-image
                         :labels
                         constants/runner-image-label])
           (pull-image! data :runner-image platform))
      data)))

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
