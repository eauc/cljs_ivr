(ns ivr.models.verbs.dial-number
  (:require [ivr.models.verb-base :as verb-base]))

(defmethod verb-base/create-type :ivr.verbs/dial-number
  [{:keys [number] :as params}]
  [:Dial
   (select-keys params [:callbackurl
                        :from
                        :record
                        :statusurl
                        :timeout
                        :waitingurl])
   [:Number number]])
