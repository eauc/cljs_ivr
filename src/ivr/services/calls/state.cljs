(ns ivr.services.calls.state
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.models.call-state :as call-state]
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
    (cond-> {:ivr.call/update {:id id :state state-update}}
      next-state (merge (call-state/emit-ticket call (assoc event :now call-time-now)))
      next-state (merge (call-state/change-event call (assoc event :now call-time-now))))))

(db/reg-event-fx
  :ivr.call/state
  [(re-frame/inject-cofx :ivr.call/time-now)]
  call-state-event)


(defn call-enter-state-event
  [{:keys [db] :as deps}
   {:keys [id] :as event}]
  (let [call (call/db-call db id)]
    (call-state/on-enter call (merge event (select-keys deps [:cloudmemory :services])))))

(db/reg-event-fx
  :ivr.call/enter-state
  [(re-frame/inject-cofx :ivr.cloudmemory/cofx)
   (re-frame/inject-cofx :ivr.services/cofx)]
  call-enter-state-event)


(defn call-leave-state-event
  [{:keys [db] :as deps}
   {:keys [id] :as event}]
  (let [call (call/db-call db id)]
    (call-state/on-leave call (merge event (select-keys deps [:cloudmemory :acd])))))

(db/reg-event-fx
  :ivr.call/leave-state
  [(re-frame/inject-cofx :ivr.acd/cofx)
   (re-frame/inject-cofx :ivr.cloudmemory/cofx)]
  call-leave-state-event)
