(ns ivr.routes.action
  (:require [cljs.nodejs :as nodejs]
            [ivr.db :as db]
            [ivr.models.script :as script]
            [ivr.models.store :as store]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [ivr.services.calls]
            [re-frame.core :as re-frame]))

(defonce express (nodejs/require "express"))

(def base-url
  (str (get-in url/config [:apis :v1 :link])
       (get-in url/config [:apis :v1 :action :link])))

(def script-start-url
  (get-in url/config [:apis :v1 :action :script-start]))

;; (def script-enter-node-url
;;   (get-in url/config [:apis :v1 :action :script-enter-node]))

;; (def script-leave-node-url
;;   (get-in url/config [:apis :v1 :action :script-leave-node]))

(def resolve-or-create-call-middleware
  (routes/dispatch [:ivr.services.calls/resolve {:create? true}]))

(def resolve-script-middleware
  (routes/dispatch [::resolve-script]))

(def script-start-route
  (routes/dispatch [:ivr.models.script/start-route]))

;; (def script-enter-node-route
;;   (routes/dispatch [:ivr.models.script/enter-node-route]))

;; (def script-leave-node-route
;;   (routes/dispatch [:ivr.models.script/leave-node-route]))

(def router
  (doto (.Router express #js {:mergeParams true})
    (.use resolve-script-middleware)
    (.use script-start-url resolve-or-create-call-middleware)
    (.get script-start-url script-start-route)
    ;; (.get script-enter-node-url script-enter-node-route)
    ;; (.get script-leave-node-url script-leave-node-route)
    ))

(defn init [app]
  (doto app
    (.use base-url router)))

(re-frame/reg-event-fx
 ::resolve-script
 [routes/interceptor
  db/default-interceptors]
 (fn resolve-script [_ [_ {:keys [req]}]]
   (let [account-id (aget req "query" "account_id")
         script-id (aget req "params" "script_id")
         on-success [::resolve-script-success {:account-id account-id}]
         on-error [::resolve-script-error {:script-id script-id}]]
     {:ivr.web/request
      (store/query
       {:type :ivr.store/get-script
        :account-id account-id
        :script-id script-id
        :on-success on-success
        :on-error on-error})})))

(re-frame/reg-event-fx
 ::resolve-script-success
 [routes/interceptor
  db/default-interceptors]
 (fn resolve-script-success [_ [_ {:keys [account-id response]} {:keys [req]}]]
   (let [script (-> response
                    (aget "body")
                    (js->clj :keywordize-keys true)
                    (script/conform {:account-id account-id}))]
     (aset req "script" script)
     {:ivr.routes/next nil})))

(re-frame/reg-event-fx
 ::resolve-script-error
 [routes/interceptor
  db/default-interceptors]
 (fn resolve-script-error [_ [_ {:keys [script-id error]}]]
   {:ivr.routes/response
    (routes/error-response
     {:status 404
      :status_code "script_not_found"
      :message "Script not found"
      :cause (assoc error :scriptid script-id)})}))
