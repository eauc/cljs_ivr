(ns ivr.services.calls
  (:require [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.services.routes :as routes]
            [ivr.services.routes.error :as routes-error]
            [re-frame.core :as re-frame]))

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
       {:status      400
        :status_code "missing_request_params"
        :message     "Missing request params"
        :cause       {:account-id (or account_id "missing")
                      :call-id    (or call_id "missing")
                      :script-id  (or script_id "missing")}})}
    (let [call (call/info->call {:id call_id
                                 :account-id account_id
                                 :application-id application_id
                                 :from from
                                 :to to
                                 :script-id script_id
                                 :time call-time-now})]
      {:db (update db :calls assoc call_id call)
       :ivr.routes/params (assoc params "call" call)
       :ivr.routes/next nil})))


(defn find-or-create-call
  [{:keys [db] :as coeffects}
   {:keys [create?] :as options}
   {{:strs [call_id] :as params} :params}]
  (let [call (get-in db [:calls call_id])]
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


(re-frame/reg-fx
  :ivr.call/action-data
  (fn call-action-data-fx
    [{:keys [info action-data] :as call}]
    (let [call-id (:id info)
          call (get-in @re-frame.db/app-db [:calls call-id])]
      (when call
        (log "info" "update call action-data" call)
        (swap! re-frame.db/app-db assoc-in [:calls call-id :action-data] action-data)))))


(re-frame/reg-cofx
  :ivr.call/time-now
  (fn call-time-now
    [coeffects]
    (assoc coeffects :call-time-now (.now js/Date))))
