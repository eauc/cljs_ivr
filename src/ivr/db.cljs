(ns ivr.db
  (:require [cljs.spec :as spec]
            [clojure.data :as data]
            [ivr.debug :refer [debug?]]
            [ivr.libs.logger :as logger]
            [ivr.services.config.spec]
            [re-frame.core :as re-frame]
            [re-frame.loggers :as re-loggers]))

(spec/def ::config-info
  :ivr.config/info)

(spec/def ::db
  (spec/keys :req-un [::config-info]))

(def log
  (logger/create "DB"))

(def check-db-interceptor
  (re-frame/->interceptor
   :id ::check-db
   :after
   (fn check-db-schema [context]
     (let [new-db (get-in context [:effects :db])]
       (if (or (nil? new-db) (spec/valid? ::db new-db))
         context
         (do
           (log "error" "spec check failed"
                (spec/explain-data ::db new-db))
           (assoc context :effects {})))))))

(re-loggers/set-loggers!
 {:log (fn [& args] (js/console.log.apply js/console (clj->js args)))
  :warn (fn [& args] (js/console.warn.apply js/console (clj->js args)))
  :error (fn [& args] (js/console.error.apply js/console (clj->js args)))
  :group (fn [& args] (js/console.group.apply js/console (clj->js args)))
  :groupEnd (fn [& args] (js/console.groupEnd.apply js/console (clj->js args)))})

(def default-interceptors
  [(when debug? re-frame/debug)
   (when debug? check-db-interceptor)])

(re-frame/reg-event-db
 ::init
 [default-interceptors]
 (fn init [db [_ config-info]]
   (assoc db :config-info config-info)))
