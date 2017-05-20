(ns ivr.db
  (:require [cljs.spec :as spec]
            [clojure.data :as data]
            [ivr.debug :refer [debug?]]
            [ivr.libs.logger :as logger]
            [ivr.services.config.spec]
            [re-frame.core :as re-frame]))

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

(def debug-interceptor
  (re-frame/->interceptor
   :id ::debug
   :before
   (fn debug-before [context]
     (let [event (get-in context [:coeffects :event])]
       (.log js/console "-- Handling reframe event:" (clj->js (first event))
             (clj->js (rest event)))
       context))
   :after
   (fn debug-after [context]
     (let [event (get-in context [:coeffects :event])
           event-name (clj->js (first event))
           orig-db (get-in context [:coeffects :db])
           new-db (or (get-in context [:effects :db]) ::not-found)]
       (if (= new-db ::not-found)
         (.log js/console "-- No :db changes caused by:" event-name)
         (let [[only-before only-after] (data/diff orig-db new-db)
               db-changed? (or (some? only-before) (some? only-after))]
           (if db-changed?
             (do (.group js/console "-- db clojure.data/diff for:" event-name)
                 (.log js/console "-- only before:" (clj->js only-before))
                 (.log js/console "-- only after :" (clj->js only-after))
                 (.groupEnd js/console))
             (.log js/console "-- no app-db changes caused by:" event-name))))
       context))))

(def default-interceptors
  [(when debug? debug-interceptor)
   (when debug? check-db-interceptor)])

(re-frame/reg-event-db
 ::init
 [default-interceptors]
 (fn init [db [_ config-info]]
   (assoc db :config-info config-info)))
