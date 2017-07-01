(ns ivr.services.calls
  (:require [clojure.set :as set]
            [ivr.models.call :as call]
            [ivr.services.calls.action]
            [ivr.services.calls.effects]
            [ivr.services.calls.resolve]
            [ivr.services.calls.state]
            [ivr.services.calls.status]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))

(re-frame/reg-cofx
  :ivr.call/time-now
  (fn call-time-now
    [coeffects]
    (assoc coeffects :call-time-now (.now js/Date))))


(defn context-route
  [_ {:keys [params]}]
  (let [call (get params "call")]
    {:ivr.routes/response
     {:data (set/rename-keys
              (merge (:info call)
                     (:action-data call))
              {:id :callid
               :account-id :accountid
               :application-id :applicationid
               :from :CALLER
               :to :CALLEE
               :script-id :scriptid
               :time :callTime})}}))


(routes/reg-action
  :ivr.call/context-route
  context-route)
