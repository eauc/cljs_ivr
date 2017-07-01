(ns ivr.models.call.terminated
  (:require [ivr.models.call-action :as call-action]
            [ivr.models.call-state :as call-state]))

(defmethod call-state/enter-ticket "Terminated"
  [call
   {:strs [cause] :as status}
   next-state]
  (if (= "user-hangup" cause)
    {:cause "CALLER_HANG_UP"}
    {:cause "IVR_HANG_UP"
     :ccapi_cause cause}))


(defmethod call-state/on-enter "Terminated"
  [{{:keys [id] :as info} :info
    action-data :action-data
    {{:keys [overflow-cause]} :info
     {:strs [cause]} :status
     {:strs [dialstatus]} :dial-status} :state
    :as call}
   {:keys [from services time] :as options}]
  (let [end-cause (call-state/end-cause call from)
        on-end-data (merge info
                           action-data
                           {:type :ivr.services/call-on-end})]
    (cond-> {:ivr.call/remove id}
      (not (= "Created" from))
      (merge {:ivr.ticket/emit
              (cond-> (call-action/call->ticket call time)
                end-cause (assoc :endCause end-cause))
              :ivr.web/request (services on-end-data)}))))
