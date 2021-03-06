(ns ivr.models.call.transferred
  (:require [ivr.models.call-state :as call-state]))


(defmethod call-state/enter-ticket "Transferred"
  [{{{:keys [sda]} :info} :state :as call}
   status
   next-state]
  {:sda sda})


(defmethod call-state/leave-ticket "Transferred"
  [{{{:keys [sda]} :info
     {:strs [bridgecause bridgeduration]} :dial-status} :state
    :as call}
   status
   next-state]
  {:sda sda
   :bridgecause bridgecause
   :bridgeduration bridgeduration})


(defmethod call-state/on-enter "Transferred"
  [{{{:keys [sda]} :info} :state :as call}
   {:keys [cloudmemory]}]
  {:ivr.web/request
   (cloudmemory {:type :ivr.cloudmemory/inc-sda-limit
                 :sda sda})})


(defmethod call-state/on-leave "Transferred"
  [{{{:keys [sda]} :info} :state :as call}
   {:keys [cloudmemory]}]
  {:ivr.web/request
   (cloudmemory {:type :ivr.cloudmemory/dec-sda-limit
                 :sda sda})})
