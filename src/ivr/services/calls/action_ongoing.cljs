(ns ivr.services.calls.action-ongoing
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
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
      (not (nil? call)) (merge {:ivr.call/action-ongoing
                                (assoc call :action-ongoing
                                       {:start-time call-time-now :action action})})
      (not (nil? ongoing)) (merge {:ivr.ticket/emit
                                   (call/call->action-ticket call call-time-now)}))))

(db/reg-event-fx
  :ivr.call/start-action
  [(re-frame/inject-cofx :ivr.call/time-now)]
  start-action)


(re-frame/reg-fx
  :ivr.call/action-ongoing
  (fn call-action-ongoing-fx
    [{:keys [info action-ongoing] :as call}]
    (let [call-id (:id info)
          call (call/db-call @re-frame.db/app-db call-id)]
      (when call
        (log "verbose" "update call action-ongoing" call)
        (swap! re-frame.db/app-db call/db-update-call call-id assoc :action-ongoing action-ongoing)))))
