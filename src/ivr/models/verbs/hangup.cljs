(ns ivr.models.verbs.hangup
  (:require [ivr.models.verb-base :as verb-base]))

(defmethod verb-base/create-type :ivr.verbs/hangup
  [_]
  [:HangUp])
