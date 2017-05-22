(ns ivr.models.verbs.redirect
  (:require [ivr.models.verb-base :as verb-base]))

(defmethod verb-base/create-type :ivr.verbs/redirect
  [{:keys [path]}]
  [:Redirect path])
