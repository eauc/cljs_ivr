(ns ivr.models.verbs
  (:require [cljs.spec :as spec]
            [ivr.models.verb-base :as verb-base]
            [ivr.models.verbs.gather]
            [ivr.models.verbs.hangup]
            [ivr.models.verbs.play]
            [ivr.models.verbs.redirect]
            [ivr.specs.verb]
            [re-frame.core :as re-frame]))

(spec/fdef create
           :args (spec/cat :verbs :ivr.verb/verbs))
(defn create [verbs]
  (let [verbs-xml [:Response (for [verb verbs] (verb-base/create-type verb))]]
    {:type "xml" :data verbs-xml}))

(re-frame/reg-cofx
 :ivr.verbs/cofx
 (fn verbs-cofx [coeffects _]
   (assoc coeffects :verbs create)))
