(ns ivr.services.calls.effects
  (:require [re-frame.core :as re-frame]
            [ivr.models.call :as call]
            [ivr.libs.logger :as logger]))


(def log
  (logger/create "callUpdate"))


(re-frame/reg-fx
  :ivr.call/create
  (fn call-create-fx
    [call]
    (log "info" "create call" {:call call})
    (swap! re-frame.db/app-db call/db-insert-call call)))


(re-frame/reg-fx
  :ivr.call/remove
  (fn call-remove-fx
    [call-id]
    (log "info" "remove call" {:call-id call-id})
    (swap! re-frame.db/app-db call/db-remove-call call-id)))


(re-frame/reg-fx
  :ivr.call/update
  (fn call-update-fx
    [{:keys [id] :as call-update}]
    (let [call (call/db-call @re-frame.db/app-db id)]
      (log "info" "update call" {:call call :update call-update})
      (swap! re-frame.db/app-db call/db-update-call id merge (dissoc call-update :id)))))
