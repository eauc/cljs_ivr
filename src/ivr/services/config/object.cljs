(ns ivr.services.config.object
  (:require [cljs.core.async :as async]
            [ivr.services.config.base :as base]))

(defmethod base/load-layer :object-layer
  [layer _]
  (async/to-chan
   [{:desc (:desc layer)
     :config (:config layer)}]))
