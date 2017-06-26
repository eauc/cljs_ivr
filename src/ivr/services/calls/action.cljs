(ns ivr.services.calls.action
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.models.call-action :as call-action]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "callsAction"))


(defn start-action
  [{:keys [db call-time-now] :as coeffects}
   {:keys [call-id action] :as event}]
  (let [call (call/db-call db call-id)
        ongoing (get call :action-ongoing)]
    (log "debug" "start-action" {:call call
                                 :ongoing ongoing})
    (cond-> {}
      (not (nil? call)) (merge {:ivr.call/update
                                {:id (call/id call)
                                 :action-ongoing {:action action
                                                  :start-time call-time-now}}})
      (not (nil? ongoing)) (merge {:ivr.ticket/emit
                                   (call-action/call->ticket call call-time-now)}))))

(db/reg-event-fx
  :ivr.call/start-action
  [(re-frame/inject-cofx :ivr.call/time-now)]
  start-action)
