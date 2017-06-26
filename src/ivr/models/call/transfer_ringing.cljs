(ns ivr.models.call.transfer-ringing
	(:require [ivr.models.call-state :as call-state]))


(defmethod call-state/enter-ticket "TransferRinging"
	[{{{:keys [sda]} :info} :state :as call}
	 status
	 next-state]
	{:ringingSda sda})


(defmethod call-state/leave-ticket "TransferRinging"
	[{{{:keys [sda]} :info
		 {:strs [dialcause dialstatus]} :dial-status} :state
		:as call}
	 status
	 next-state]
	(if-not (= "Transferred" next-state)
		{:failedSda sda
		 :dialcause dialstatus
		 :ccapi_dialcause dialcause}))
