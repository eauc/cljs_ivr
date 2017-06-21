(ns ivr.services.routes
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.services.routes.effects]
            [ivr.services.routes.error :as error]
            [ivr.services.routes.interceptor :as interceptor]))

(def log
  (logger/create "routes"))


(def default-interceptors
  [interceptor/interceptor])


(defn reg-action
  ([id interceptors handler]
   (db/reg-event-fx
     id
     (concat default-interceptors interceptors)
     (fn action-handler
       [& args]
       (try
         (apply handler args)
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
