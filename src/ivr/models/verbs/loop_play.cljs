(ns ivr.models.verbs.loop-play
  (:require [ivr.models.verb-base :as verb-base]))

(defmethod verb-base/create-type :ivr.verbs/loop-play
  [{:keys [path]}]
  [:Play
   {:loop 0}
   path])
