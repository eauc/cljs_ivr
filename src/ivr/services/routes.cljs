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
   interceptor/interceptor])


(defn reg-action
  ([id interceptors handler {:keys [with-cofx?]}]
   (re-frame/reg-event-fx
     id
     (concat default-interceptors interceptors)
     (fn action-handler
       [coeffects event]
       (try
         (if with-cofx?
           (apply handler coeffects event)
           (apply handler event))
         (catch js/Object error
           {:ivr.routes/response
            (error-response
              {:status 500
               :statusCode "internal_error"
               :message "Internal error"
               :cause {:message (aget error "message")
                       :stack (aget error "stack")}})})))))
  ([id interceptors handler]
   (reg-action id interceptors handler false))
  ([id handler]
   (reg-action id [] handler false)))
