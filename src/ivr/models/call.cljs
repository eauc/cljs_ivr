(ns ivr.models.call
  (:require [clojure.set :as set]))

(defn info->call [info]
  {:info (select-keys info [:id :account-id :application-id :from :to :script-id :time])
   :action-data {}
   :action-ongoing nil})


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


(defn db-insert-call
  [db {{:keys [id]} :info :as call}]
  (update db :calls assoc id call))


(defn db-update-call
  [db call-id & args]
  (apply update-in db [:calls call-id] args))


(defn db-call
  [db call-id]
  (get-in db [:calls call-id]))
