(ns ivr.services.calls
  (:require [cljs.core.match :refer-macros [match]]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.services.calls.action-data]
            [ivr.services.calls.action-ongoing]
            [ivr.services.routes :as routes]
            [ivr.services.routes.error :as routes-error]
            [re-frame.core :as re-frame]
            [ivr.db :as db]))


(def log
  (logger/create "calls"))


(defn- try-to-create-call
  [{:keys [db call-time-now] :as coeffects}
   {:strs [account_id application_id call_id from to script_id] :as params}]
  (if (or (nil? account_id)
          (nil? call_id)
          (nil? script_id))
    {:ivr.routes/response
     (routes-error/error-response
       {:status                  400
        :status_code "missing_request_params"
        :message                 "Missing request params"
        :cause                   {:account-id (or account_id "missing")
                                  :call-id              (or call_id "missing")
                                  :script-id    (or script_id "missing")}})}
    (let [call (call/info->call {:id call_id
                                 :account-id account_id
                                 :application-id application_id
                                 :from from
                                 :to to
                                 :script-id script_id
                                 :time call-time-now})]
      {:db (call/db-insert-call db call)
       :ivr.routes/params (assoc params "call" call)
       :ivr.routes/next nil})))


(defn find-or-create-call
  [{:keys [db] :as coeffects}
   {:keys [create?] :as options}
   {{:strs [call_id] :as params} :params}]
  (let [call (call/db-call db call_id)]
    (cond
      (and (nil? call)
           (not create?)) {:ivr.routes/response
                           (routes-error/error-response
                             {:status 404
                              :status_code "call_not_found"
                              :message "Call not found"
                              :cause {:call-id call_id}})}
      (nil? call) (try-to-create-call coeffects params)
      :else {:ivr.routes/params (assoc params "call" call)
             :ivr.routes/next nil})))


(routes/reg-action
  :ivr.call/resolve
  [(re-frame/inject-cofx :ivr.call/time-now)]
  find-or-create-call)


(re-frame/reg-cofx
  :ivr.call/time-now
  (fn call-time-now
    [coeffects]
    (assoc coeffects :call-time-now (.now js/Date))))


(def terminal-status
  #{"canceled" "completed" "failed"})


(defn call-status-route
  [_ {:keys [params] :as route}]
  (let [{:strs [call status]} params
        call-update (cond-> {:id (get-in call [:info :id])
                             :status (select-keys params ["status" "cause"])}
                      (terminal-status status) (assoc :next-state "Terminated"))]
    {:ivr.routes/response {:status 204}
     :dispatch [:ivr.call/update call-update]}))

(routes/reg-action
  :ivr.call/status-route
  call-status-route)


(defn call-dial-status-route
  [_ {:keys [params] :as route}]
  (let [{:strs [call bridgestatus]} params
        current-state (get-in call [:state :current])
        call-update (cond-> {:id (get-in call [:info :id])}
                      (and (= "TransferRinging" current-state)
                           (= "in-progress" bridgestatus)) (assoc :next-state "Transferred"))]
    {:ivr.routes/response {:status 204}
     :dispatch [:ivr.call/update call-update]}))

(routes/reg-action
  :ivr.call/dial-status-route
  call-dial-status-route)


(defn state-ticket
  [state {:keys [next-state now] :as info}]
  (let [duration (- now (:start-time state))]
    (merge {:state (:current state)
            :nextState next-state
            :time now
            :duration duration}
           (dissoc info :next-state :now))))


(defn emit-state-ticket
  [{:keys [state] :as call}
   {:keys [now next-state queue sda] :as update}]
  (let [duration (- now (:start-time state))]
    (match [(:current state) next-state]
           ["Created" "Terminated"] {}
           ["InProgress" "InProgress"] {}
           ["AcdTransferred" "AcdTransferred"] {}
           ["Created" _] {:ivr.ticket/emit
                          (state-ticket state {:next-state next-state
                                               :now (:start-time state)})}
           [_ "AcdTransferred"] {:ivr.ticket/emit
                                 (state-ticket state {:next-state next-state
                                                      :now now
                                                      :queueid queue})}
           [_ "TransferRinging"] {:ivr.ticket/emit
                                  (state-ticket state {:next-state next-state
                                                       :now now
                                                       :ringingSda sda})}
           :else {:ivr.ticket/emit
                  (state-ticket state {:next-state next-state
                                       :now now})})))


(defn call-update-handler
  [{:keys [call-time-now db]}
   {:keys [id next-state status] :as event}]
  (let [call (call/db-call db id)]
    (cond-> (if (= "Terminated" next-state)
              {:db (call/db-remove-call db id)}
              {:db (cond-> db
                     next-state (call/db-update-call id assoc :state {:current next-state
                                                                      :start-time call-time-now})
                     status (call/db-update-call id update :status merge status))})
      next-state (merge (emit-state-ticket call (assoc event :now call-time-now))))))

(db/reg-event-fx
  :ivr.call/update
  [(re-frame/inject-cofx :ivr.call/time-now)]
  call-update-handler)
