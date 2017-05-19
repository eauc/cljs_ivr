(ns ivr.services.config.object
  (:require [ivr.services.config.base :as base]))

(defmethod base/load-layer :object-layer
  [layer _]
  (.resolve
   js/Promise
   {:desc (:desc layer)
    :config (:config layer)}))
