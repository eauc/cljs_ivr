(ns ivr.models.verbs.play
  (:require [ivr.models.verb-base :as verb-base]))

(defmethod verb-base/create-type :ivr.verbs/play
  [{:keys [path]}]
  [:Play path])
