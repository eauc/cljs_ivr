(ns ivr.services.config.invalid
  (:require [cljs.core.async :as async]
            [ivr.services.config.base :as base :refer [log]]))

(defmethod base/load-layer :invalid-layer
  [layer _]
  (async/to-chan
   [(log "error" "Invalid"
         {:desc layer
          :config {}
          :error "Invalid layer"})]))
