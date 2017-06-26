(ns ivr.models.call.terminated
  (:require [ivr.models.call-state :as call-state]))


(defmethod call-state/enter-ticket "Terminated"
  [call
   {:strs [cause] :as status}
   next-state]
  (if (= "user-hangup" cause)
    {:cause "CALLER_HANG_UP"}
    {:cause "IVR_HANG_UP"
     :ccapi_cause cause}))
