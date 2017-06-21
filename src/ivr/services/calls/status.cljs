(ns ivr.services.calls.status
  (:require [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.services.routes :as routes]))

(def log
  (logger/create "callsStatus"))


(def terminal-status
  #{"canceled" "completed" "failed"})


(defn call-status-route
  [_ {:keys [params] :as route}]
  (let [{:strs [call status]} params
        state-update (cond-> {:id (call/id call)
                              :status (select-keys params ["status" "cause"])}
                       (terminal-status status) (assoc :next-state "Terminated"))]
    {:ivr.routes/response {:status 204}
     :dispatch [:ivr.call/state state-update]}))

(routes/reg-action
  :ivr.call/status-route
  call-status-route)


(defn call-dial-status-route
  [_ {:keys [params] :as route}]
  (let [{:strs [call bridgestatus]} params
        current-state (call/current-state call)
        call-update (cond-> {:id (call/id call)
                             :dial-status (select-keys params ["bridgecause"
                                                               "bridgeduration"
                                                               "dialstatus"
                                                               "dialcause"])}
                      (and (= "TransferRinging" current-state)
                           (= "in-progress" bridgestatus)) (assoc :next-state "Transferred"))]
    {:ivr.routes/response {:status 204}
     :dispatch [:ivr.call/state call-update]}))

(routes/reg-action
  :ivr.call/dial-status-route
  call-dial-status-route)
