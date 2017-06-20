(ns ivr.services.routes
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.services.routes.dispatch :as dispatch]
            [ivr.services.routes.effects]
            [ivr.services.routes.error :as error]
            [ivr.services.routes.interceptor :as interceptor]
            [re-frame.core :as re-frame]))


(def log
  (logger/create "routes"))


(def default-interceptors
  [db/default-interceptors
   interceptor/interceptor])


(defn reg-action
  ([id interceptors handler]
   (re-frame/reg-event-fx
     id
     (concat default-interceptors interceptors)
     (fn action-handler
       [coeffects event]
       (try
         (apply handler coeffects event)
         (catch js/Object error
           {:ivr.routes/response
            (error/error-response
              {:status 500
               :statusCode "internal_error"
               :message "Internal error"
               :cause {:message (aget error "message")
                       :stack (aget error "stack")}})})))))
  ([id handler]
   (reg-action id [] handler)))


(reg-action
  :ivr.routes/error
  (fn routes-error
    [_ error]
    {:ivr.routes/response
     (error/error-response error)}))
