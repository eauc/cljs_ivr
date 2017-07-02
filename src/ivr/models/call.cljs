(ns ivr.models.call
  (:require [cljs.core.match :refer-macros [match]]
            [clojure.set :as set]
            [ivr.libs.logger :as logger]
            [ivr.models.call-action :as call-action]
            [ivr.models.call-number :as call-number]
            [ivr.models.call-state :as call-state]
            [ivr.models.call.acd-transferred]
            [ivr.models.call.in-progress]
            [ivr.models.call.terminated]
            [ivr.models.call.transferred]
            [ivr.models.call.transfer-ringing]
            [ivr.models.cloudmemory :as cloudmemory]))

(def log
  (logger/create "call"))


(defn info->call [{:keys [time] :as info}]
  (let [call-info (select-keys info [:id :account-id :application-id :from :to :script-id :time])]
    {:info call-info
     :state {:current "Created" :start-time time}
     :action-data (merge (set/rename-keys call-info {:id "callid"
                                                     :account-id "accountid"
                                                     :application-id "applicationid"
                                                     :from "CALLER"
                                                     :to "CALLEE"
                                                     :script-id "scriptid"
                                                     :time "callTime"})
                         (call-number/geo-localize-call info))
     :action-ongoing nil}))


(defn id
  [call]
  (get-in call [:info :id]))


(defn created-time
  [call]
  (get-in call [:info :time]))


(def current-state call-state/current-state)


(defn current-sda
  [call]
  (get-in call [:state :info :sda]))


(defn update-sda
  [call sda]
  (let [id (id call)
        state (current-state call)
        current-sda (current-sda call)
        info-update (cond-> {:sda sda}
                      (and (= "TransferRinging" state)
                           current-sda) (assoc :failed-sda current-sda))]
    [:ivr.call/state {:id id :info info-update}]))


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
