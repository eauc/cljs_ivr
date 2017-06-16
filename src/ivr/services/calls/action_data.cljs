(ns ivr.services.calls.action-data
  (:require [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "callsAction"))


(re-frame/reg-fx
  :ivr.call/action-data
  (fn call-action-data-fx
    [{:keys [info action-data] :as call}]
    (let [call-id (:id info)
          call (call/db-call @re-frame.db/app-db call-id)]
      (when call
        (log "verbose" "update call action-data" call)
        (swap! re-frame.db/app-db call/db-update-call call-id assoc :action-data action-data)))))
