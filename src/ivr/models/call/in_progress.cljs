(ns ivr.models.call.in-progress
  (:require [ivr.models.call-state :as call-state]))


(defmethod call-state/end-cause "InProgress"
  [{{{:strs [cause]} :status} :state
    :as call} _]
  (if (= "user-hangup" cause)
    "CALLER_HANG_UP"
    "IVR_HANG_UP"))
