(ns ivr.models.call-action
  (:require [clojure.set :as set]))


(defn call->ticket
	[{:keys [info action-ongoing] :as call} now]
	(let [duration (- now (:start-time action-ongoing))]
		(merge {:producer "IVR" :subject "ACTION"}
					 (set/rename-keys info {:id :callid
																	:account-id :accountid
																	:application-id :applicationid
																	:script-id :scriptid
																	:time :callTime})
					 {:time now
						:duration duration
						:action (:action action-ongoing)})))
