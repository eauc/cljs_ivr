(ns ivr.services.calls
  (:require [ivr.services.calls.action]
            [ivr.services.calls.effects]
            [ivr.services.calls.resolve]
            [ivr.services.calls.state]
            [ivr.services.calls.status]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]
            [ivr.models.call :as call]))

(re-frame/reg-cofx
  :ivr.call/time-now
  (fn call-time-now
    [coeffects]
    (assoc coeffects :call-time-now (.now js/Date))))


(defn context-route
  [_ {:keys [params]}]
  (let [call (get params "call")]
    {:ivr.routes/response
     {:data (merge (:info call)
                   (:action-data call))}}))


(routes/reg-action
  :ivr.call/context-route
  context-route)
