(ns ivr.models.call
  (:require [clojure.set :as set]
            [ivr.libs.logger :as logger]))


(def log
  (logger/create "call"))


(defn info->call [{:keys [time] :as info}]
  {:info (select-keys info [:id :account-id :application-id :from :to :script-id :time])
   :state {:current "Created" :start-time time}
   :action-data {}
   :action-ongoing nil})


(defn id
  [call]
  (get-in call [:info :id]))


(defn created-time
  [call]
  (get-in call [:info :time]))


(defn current-state
  [call]
  (get-in call [:state :current]))


(defn call->action-ticket
  [{:keys [info action-ongoing] :as call} now]
  (let [duration (- now (:start-time action-ongoing))]
    (merge {:producer "IVR" :subject "ACTION"}
           (set/rename-keys info {:id :callid
                                  :account-id :accountid
                                  :application-id :applicationid
                                  :script-id :scriptid
                                  :time :callTime})
           {:time now
            :duration duration
            :action (:action action-ongoing)})))


(defn db-call
  [db call-id]
  (get-in db [:calls call-id]))


(defn db-insert-call
  [db {{:keys [id]} :info :as call}]
  (if-not (db-call db id)
    (update db :calls assoc id call)
    db))


(defn db-remove-call
  [db call-id]
  (update db :calls dissoc call-id))


(defn db-update-call
  [db call-id & args]
  (if (db-call db call-id)
    (apply update-in db [:calls call-id] args)
    db))
