(ns ivr.models.cloudmemory
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "cloudmemory"))


(defmulti query :type)

(re-frame/reg-cofx
  :ivr.cloudmemory/cofx
  (fn cloudmemory-cofx [coeffects _]
    (assoc coeffects :cloudmemory query)))


(defmethod query :default
  [params]
  (log "error" "unknown query" params))


(defmethod query :ivr.cloudmemory/inc-sda-limit
  [{:keys [sda] :as params}]
  {:method "GET"
   :url (str "/cloudmemory/counter/" sda "/incr")
   :on-success [:ivr.cloudmemory/sda-limit-success {:sda sda :action "inc"}]
   :on-error [:ivr.cloudmemory/sda-limit-error {:sda sda :action "inc"}]})


(defmethod query :ivr.cloudmemory/dec-sda-limit
  [{:keys [sda] :as params}]
  {:method "GET"
   :url (str "/cloudmemory/counter/" sda "/decr")
   :on-success [:ivr.cloudmemory/sda-limit-success {:sda sda :action "dec"}]
   :on-error [:ivr.cloudmemory/sda-limit-error {:sda sda :action "dec"}]})


(defn sda-limit-success
  [_ params]
  (log "info" "sda limit ok" params)
  {})

(db/reg-event-fx
  :ivr.cloudmemory/sda-limit-success
  sda-limit-success)


(defn sda-limit-error
  [_ params]
  (log "error" "sda limit error" params)
  {})

(db/reg-event-fx
  :ivr.cloudmemory/sda-limit-error
  sda-limit-error)
