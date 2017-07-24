(ns ivr.models.verbs
  (:require [cljs.spec.alpha :as spec]
            [ivr.libs.logger :as logger]
            [ivr.models.verb-base :as verb-base]
            [ivr.models.verbs.dial-number]
            [ivr.models.verbs.gather]
            [ivr.models.verbs.hangup]
            [ivr.models.verbs.loop-play]
            [ivr.models.verbs.play]
            [ivr.models.verbs.record]
            [ivr.models.verbs.redirect]
            [ivr.specs.verb]
            [re-frame.core :as re-frame]))


(def log
  (logger/create "verbs"))


(spec/fdef create
           :args (spec/cat :verbs :ivr.verb/verbs))
(defn create [verbs]
  (let [verbs-xml [:Response (for [verb verbs]
                               (verb-base/create-type
                                (log "silly" "create verb" verb)))]]
    {:type "xml" :data verbs-xml}))

(re-frame/reg-cofx
 :ivr.verbs/cofx
 (fn verbs-cofx [coeffects _]
   (assoc coeffects :verbs create)))


(defn first-verb
  [response]
  (get (first (get-in response [:data 1])) 0))
