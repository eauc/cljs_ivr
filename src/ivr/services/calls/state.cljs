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
    (cond-> {:ivr.call/update {:id id :state state-update}}
      next-state (merge (call/emit-state-ticket call (assoc event :now call-time-now)))
      next-state (merge (call/change-state-event call (assoc event :now call-time-now))))))

(db/reg-event-fx
  :ivr.call/state
  [(re-frame/inject-cofx :ivr.call/time-now)]
  call-state-event)


(defn call-enter-state-event
  [{:keys [db services] :as deps}
   {:keys [id time from to]}]
  (let [call (call/db-call db id)]
    (condp = to
      "Transferred" (call/inc-sda-limit call deps)
      "Terminated" (call/terminate call {:from from :services services :time time})
      {})))

(db/reg-event-fx
  :ivr.call/enter-state
  [(re-frame/inject-cofx :ivr.cloudmemory/cofx)
   (re-frame/inject-cofx :ivr.services/cofx)]
  call-enter-state-event)


(defn call-leave-state-event
  [{:keys [acd db] :as deps} {:keys [id time from to]}]
  (let [call (call/db-call db id)]
    (condp = from
      "Transferred" (call/dec-sda-limit call deps)
      "AcdTransferred" (call/update-acd-status call {:acd acd :next-state to :time time})
      {})))

(db/reg-event-fx
  :ivr.call/leave-state
  [(re-frame/inject-cofx :ivr.acd/cofx)
   (re-frame/inject-cofx :ivr.cloudmemory/cofx)]
  call-leave-state-event)
