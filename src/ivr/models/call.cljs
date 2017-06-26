(ns ivr.models.call
  (:require [cljs.core.match :refer-macros [match]]
            [clojure.set :as set]
            [ivr.libs.logger :as logger]
            [ivr.models.call-action :as call-action]
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


(def call->action-ticket call-action/call->action-ticket)


(defn emit-state-ticket
  [{{current-state :current
     start-time :start-time
     {:keys [failed-sda overflow-cause sda]} :info
     {:strs [dialcause dialstatus]} :dial-status} :state
    :as call}
   {:keys [now next-state status] :as update}]
  (let [{:strs [cause]} status
        duration (- now start-time)
        base-ticket {:state current-state
                     :nextState next-state
                     :time now
                     :duration duration}
        emit-ticket? (match [current-state next-state]
                            [_ "Created"] false
                            ["Created" "Terminated"] false
                            ["InProgress" "InProgress"] false
                            ["AcdTransferred" "AcdTransferred"] false
                            [(nil :<< #{"TransferRinging"}) "Transferred"] false
                            ["Transferred" (nil :<< #{"Terminated"})] false
                            ["Terminated" _] false
                            :else true)]
    (if emit-ticket?
      (match [current-state next-state]
             ["AcdTransferred" "Terminated"]
             {:ivr.ticket/emit
              (if (and overflow-cause (= "xml-hangup" cause))
                (merge base-ticket
                       (call-state/leave-ticket call status next-state)
                       (call-state/enter-ticket call status next-state))
                base-ticket)}
             ["Transferred" "Terminated"]
             {:ivr.ticket/emit
              (merge base-ticket
                     (call-state/leave-ticket call status next-state))}
             ["TransferRinging" "TransferRinging"]
             {:ivr.ticket/emit
              (merge base-ticket
                     {:failedSda failed-sda
                      :dialcause dialstatus
                      :ccapi_dialcause dialcause}
                     (call-state/enter-ticket call status next-state))}
             ["TransferRinging" "Terminated"]
             {:ivr.ticket/emit
              (cond
                (#{"failed" "no-answer" "busy"} dialstatus)
                (merge base-ticket
                       (call-state/leave-ticket call status next-state)
                       {:cause "IVR_HANG_UP"
                        :ccapi_cause cause})
                (= "user-hangup" cause)
                (merge base-ticket
                       {:ringingSda sda}
                       (call-state/enter-ticket call status next-state))
                :else base-ticket)}
             :else
             {:ivr.ticket/emit
              (merge base-ticket
                     (call-state/leave-ticket call status next-state)
                     (call-state/enter-ticket call status next-state))}))))


(defn change-state-event
  [call {:keys [next-state now] :as update}]
  (let [id (id call)
        current-state (current-state call)
        event {:id id :time now :from current-state :to next-state}]
    (if-not (= current-state next-state)
      {:dispatch-n [[:ivr.call/leave-state event]
                    [:ivr.call/enter-state event]]})))


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
