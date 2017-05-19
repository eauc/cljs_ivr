(ns ivr.services.config.invalid
  (:require [ivr.services.config.base :as base :refer [log]]))

(defmethod base/load-layer :invalid-layer
  [layer _]
  (.resolve
   js/Promise
   (log "error" "Invalid"
        {:desc layer
         :config {}
         :error "Invalid layer"})))
