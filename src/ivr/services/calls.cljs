(ns ivr.services.calls
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.services.routes :as routes]
            [ivr.specs.call]
            [re-frame.core :as re-frame]))

(defn- try-to-create-call [id db {:keys [account-id script-id]}]
  (if (or (nil? id)
          (nil? account-id)
          (nil? script-id))
    {:ivr.routes/response
     (routes/error-response
      {:status 400
       :status_code "missing_request_params"
       :message "Missing request params"
       :cause {:call-id (or id "missing")
               :account-id (or account-id "missing")
               :script-id (or script-id "missing")}})}
    {:db (update db :calls assoc id
                 (call/info->call {:id id
                                   :account-id account-id
                                   :script-id script-id}))
     :ivr.routes/next nil}))


(defn find-or-create-call [id db {:keys [create? account-id script-id] :as options}]
  (let [call (get-in db [:calls id])]
    (cond
      (and (nil? call)
           (not create?)) {:ivr.routes/response
                           (routes/error-response
                            {:status 404
                             :status_code "call_not_found"
                             :message "Call not found"
                             :cause {:call-id id}})}
      (nil? call) (try-to-create-call id db options)
      :else {:ivr.routes/next nil})))


(re-frame/reg-event-fx
 ::resolve
 [routes/interceptor
  db/default-interceptors]
 (fn calls-resolve [{:keys [db]} [_ {:keys [create?]} {:keys [req]}]]
   (let [id         (aget req "query" "call_id")
         account-id (aget req "query" "account_id")
         script-id  (aget req "params" "script_id")]
     (find-or-create-call id db {:create?     create?
                                 :account-id  account-id
                                 :script-id   script-id}))))


(re-frame/reg-fx
 :ivr.call/action-data
 (fn call-action-data-fx [{:keys [call-id data]}]
   (let [call (get-in @re-frame.db/app-db [:calls call-id])]
     (when call
       (swap! re-frame.db/app-db assoc-in [:calls call-id :action-data] data)))))
