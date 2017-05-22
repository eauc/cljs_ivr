(ns ivr.models.verbs.gather
  (:require [ivr.models.verb-base :as verb-base]))


(defn- gather-play [play]
  (if (string? play)
    [:Play play]
    [:Speak
     {:locutor (:voice play)}
     (:text play)]))

(defmethod verb-base/create-type :ivr.verbs/gather
  [{:keys [callbackmethod play] :as params}]
  [:Gather
   (-> params
       (select-keys [:finishonkey
                     :numdigits
                     :timeout
                     :callbackurl])
       (assoc :callbackmethod (or callbackmethod "POST")))
   (for [p play] (gather-play p))])
