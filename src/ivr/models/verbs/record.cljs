(ns ivr.models.verbs.record
  (:require [ivr.models.verb-base :as verb-base]))

(defmethod verb-base/create-type :ivr.verbs/record
  [params]
  [:Record
   (merge {:playbeep true}
          (select-keys params [:finishonkey :maxlength :callbackurl]))])
