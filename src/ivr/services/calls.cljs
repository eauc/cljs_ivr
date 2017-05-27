(ns ivr.services.calls
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.services.routes :as routes]
            [ivr.specs.call]
            [re-frame.core :as re-frame]))

(defn- try-to-create-call [db {:keys [account_id call_id script_id] :as params}]
  (if (or (nil? account_id)
          (nil? call_id)
          (nil? script_id))
    {:ivr.routes/response
     (routes/error-response
      {:status      400
       :status_code "missing_request_params"
       :message     "Missing request params"
       :cause       {:account-id (or account_id "missing")
                     :call-id    (or call_id "missing")
                     :script-id  (or script_id "missing")}})}
    (let [call (call/info->call {:id call_id
                                 :account-id account_id
                                 :script-id script_id})]
      {:db (update db :calls assoc call_id call)
       :ivr.routes/params (assoc params :call call)
       :ivr.routes/next nil})))


(defn find-or-create-call [db create? {:keys [call_id] :as params}]
  (let [call (get-in db [:calls call_id])]
    (cond
      (and (nil? call)
           (not create?)) {:ivr.routes/response
                           (routes/error-response
                            {:status 404
                             :status_code "call_not_found"
                             :message "Call not found"
                             :cause {:call-id call_id}})}
      (nil? call) (try-to-create-call db params)
      :else {:ivr.routes/params (assoc params :call call)
             :ivr.routes/next nil})))


(re-frame/reg-event-fx
 ::resolve
 [routes/interceptor
  db/default-interceptors]
 (fn calls-resolve [{:keys [db]} [_ {:keys [create?]} {:keys [params]}]]
   (find-or-create-call db create? params)))


(re-frame/reg-fx
 :ivr.call/action-data
 (fn call-action-data-fx [{:keys [call-id data]}]
   (let [call (get-in @re-frame.db/app-db [:calls call-id])]
     (when call
       (swap! re-frame.db/app-db assoc-in [:calls call-id :action-data] data)))))