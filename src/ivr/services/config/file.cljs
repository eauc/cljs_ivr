(ns ivr.services.config.file
  (:require [cljs.nodejs :as nodejs]
            [ivr.services.config.base :as base :refer [log]]))

(defonce path-lib (nodejs/require "path"))

(defmethod base/load-layer :file-layer
  [layer]
  (.resolve
   js/Promise
   (try
     (let [path (:path layer)
           relative-path (.join path-lib (.cwd js/process) path)
           config (-> relative-path
                      (nodejs/require)
                      (js->clj  :keywordize-keys true))]
       {:desc path
        :config config})
     (catch js/Object error
       (log "error" "Load file"
            {:desc (:path layer)
             :config {}
             :error (aget error "message")})))))
