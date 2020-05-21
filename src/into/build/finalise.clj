(ns into.build.finalise
  (:require [into
             [flow :as flow]
             [log :as log]]))

(defn- log-success!
  [{:keys [target-image started-at] :as data}]
  (let [delta (/ (- (System/nanoTime) started-at) 1e9)]
    (if target-image
      (log/success "Image [%s] has been built successfully. (%.3fs)"
                   target-image
                   delta)
      (log/success "Artifacts have been built successfully. (%.3fs)"
                   delta)))
  data)

(defn run
  [data]
  (-> data
      (log/report-errors)
      (flow/with-flow->
        (log-success!))))
