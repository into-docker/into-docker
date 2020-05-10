(ns into.build.finalise
  (:require [into
             [flow :as flow]
             [log :as log]]))

(defn- log-success!
  [{:keys [target-image] :as data}]
  (if target-image
    (log/success "Image [%s] has been built successfully." target-image)
    (log/success "Artifacts have been built successfully."))
  data)

(defn run
  [data]
  (-> data
      (log/report-errors)
      (flow/with-flow->
        (log-success!))))
