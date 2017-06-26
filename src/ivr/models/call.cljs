(ns ivr.models.call
  (:require [cljs.core.match :refer-macros [match]]
            [clojure.set :as set]
            [ivr.libs.logger :as logger]
            [ivr.models.call-state :as call-state]
            [ivr.models.call.acd-transferred]
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


(defn inc-sda-limit
  [{{{:keys [sda]} :info} :state :as call}
   {:keys [cloudmemory]}]
  {:ivr.web/request
   (cloudmemory {:type :ivr.cloudmemory/inc-sda-limit
                 :sda sda})})


(defn dec-sda-limit
  [{{{:keys [sda]} :info} :state :as call}
   {:keys [cloudmemory]}]
  {:ivr.web/request
   (cloudmemory {:type :ivr.cloudmemory/dec-sda-limit
                 :sda sda})})


(defn update-acd-status
  [{{:keys [account-id id]} :info
    {{:strs [cause status]} :status} :state
    :as call}
   {:keys [acd next-state time]}]
  {:ivr.web/request
   (acd {:type :ivr.acd/update-call-status
         :account-id account-id
         :call-id id
         :status (or status "in-progress")
         :cause cause
         :IVRStatus {:state next-state
                     :lastChange time}})})


(defn terminate
  [{{:keys [id] :as info} :info
    action-data :action-data
    {{:keys [overflow-cause]} :info
     {:strs [cause]} :status
     {:strs [dialstatus]} :dial-status} :state
    :as call}
   {:keys [from services time]}]
  (let [end-cause (condp = from
                    "AcdTransferred" (if (and overflow-cause
                                              (= "xml-hangup" cause))
                                       "IVR_HANG_UP"
                                       "")
                    "InProgress" (if (= "user-hangup" cause)
                                   "CALLER_HANG_UP"
                                   "IVR_HANG_UP")
                    "TransferRinging" (cond
                                        (#{"failed" "no-answer" "busy"} dialstatus) "IVR_HANG_UP"
                                        (= "user-hangup" cause) "CALLER_HANG_UP"
                                        :else nil)
                    nil)
        on-end-data (merge info
                           action-data
                           {:type :ivr.services/call-on-end})]
    (cond-> {:ivr.call/remove id
             :ivr.web/request (services on-end-data)}
      end-cause (merge {:ivr.ticket/emit
                        (assoc (call->action-ticket call time) :endCause end-cause)}))))


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
