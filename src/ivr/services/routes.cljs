(ns ivr.services.routes
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.services.routes.dispatch :as dispatch]
            [ivr.services.routes.effects]
            [ivr.services.routes.interceptor :as interceptor]
            [ivr.specs.route]
            [re-frame.core :as re-frame]))


(def log
  (logger/create "routes"))


(defn error-response
  [{:keys [status]
    :or {status 500}
    :as data}]
  {:status status
   :data data})


(def default-interceptors
  [db/default-interceptors
   interceptor/interceptor
   (re-frame/inject-cofx :ivr.store/cofx)
   (re-frame/inject-cofx :ivr.verbs/cofx)])


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
            (error-response
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
     (error-response error)}))
