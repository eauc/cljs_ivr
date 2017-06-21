(ns ivr.services.calls
  (:require [ivr.services.calls.action]
            [ivr.services.calls.effects]
            [ivr.services.calls.resolve]
            [ivr.services.calls.state]
            [ivr.services.calls.status]
            [re-frame.core :as re-frame]))

(re-frame/reg-cofx
  :ivr.call/time-now
  (fn call-time-now
    [coeffects]
    (assoc coeffects :call-time-now (.now js/Date))))
