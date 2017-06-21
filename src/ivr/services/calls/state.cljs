(ns ivr.services.calls.state
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [re-frame.core :as re-frame]))


(def log
  (logger/create "callsState"))


(defn call-state-event
  [{:keys [call-time-now db]}
   {:keys [id next-state info status dial-status] :as event}]
  (let [{:keys [state] :as call} (call/db-call db id)
        state-update
        (cond-> state
          next-state (merge {:current next-state :start-time call-time-now})
          info (update :info merge info)
          status (update :status merge status)
          dial-status (update :dial-status merge dial-status))]
    (cond-> (if (= "Terminated" next-state)
              {:ivr.call/remove id}
              {:ivr.call/update {:id id :state state-update}})
      next-state (merge (call/emit-state-ticket call (assoc event :now call-time-now))))))

(db/reg-event-fx
  :ivr.call/state
  [(re-frame/inject-cofx :ivr.call/time-now)]
  call-state-event)
