(ns into.flow.pull
  (:require [into.flow
             [core :as flow]
             [log :as log]]
            [into.docker :as docker]
            [into.utils
             [data :as data]
             [labels :as labels]]))

;; ## Pull Logic

(defn- pull-image-if-not-exists!
  [{:keys [client] :as data} {:keys [full-name]}]
  (log/debug data "  Pulling image [%s] ..." full-name)
  (or (docker/inspect-image client full-name)
      (do (docker/pull-image client full-name)
          (docker/inspect-image client full-name))))

(defn- create-image-instance!
  [data image]
  (when-let [{:keys [Config]} (pull-image-if-not-exists! data image)]
    {:image       (assoc image :hash (:Image Config))
     :labels      (into {} (:Labels Config))
     :cmd         (into [] (:Cmd Config))}))

(defn- pull-image-instance!
  [data spec-key instance-key]
  (let [image (get-in data [:spec spec-key])]
    (-> (->> (create-image-instance! data image)
             (assoc-in data [:instances instance-key]))
        (flow/validate
         [:instances instance-key]
         (str "Image not found: " (:full-name image))))))

;; ## Select Logic

(defn- select-runner-image
  [data]
  (-> (or (some->> (data/instance data :builder)
                   (labels/get-runner-image)
                   (data/->image)
                   (update-in data [:spec :runner-image] #(or %1 %2)))
          data)
      (flow/validate [:spec :runner-image] "No runner image given.")))

(defn- overwrite-runner-cmd
  [data]
  (or (some->> (data/instance data :builder)
               (labels/get-runner-cmd)
               (vector "sh" "-c")
               (assoc-in data [:instances :runner :cmd]))
      data))

;; ## Flow

(defn run
  [data]
  (flow/with-flow-> data
    (log/info "Pulling images ...")
    (pull-image-instance! :builder-image :builder)
    (select-runner-image)
    (pull-image-instance! :runner-image :runner)
    (overwrite-runner-cmd)))
