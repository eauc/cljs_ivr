(ns ivr.models.call.acd-transferred
  (:require [ivr.models.call-state :as call-state]))


(defmethod call-state/enter-ticket "AcdTransferred"
  [{{{:keys [queue]} :info} :state :as call}
   status
   next-state]
  {:queueid queue})


(defmethod call-state/leave-ticket "AcdTransferred"
  [{{{:keys [overflow-cause]} :info} :state :as call}
   status
   next-state]
  {:acdcause "ACD_OVERFLOW"
   :overflowcause overflow-cause})
