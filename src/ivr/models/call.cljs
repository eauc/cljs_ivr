(ns ivr.models.call
  (:require [cljs.core.match :refer-macros [match]]
            [clojure.set :as set]
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


(defn enter-state-ticket
  [{{current-state :current
     {:keys [overflow-cause queue sda]} :info
     {:strs [dialstatus]} :dial-status} :state
    :as call}
   {:strs [cause] :as status}
   next-state]
  (condp = next-state
    "AcdTransferred" {:queueid queue}
    "Transferred" {:sda sda}
    "TransferRinging" {:ringingSda sda}
    "Terminated" (cond
                   (= "Transferred" current-state) {}
                   (= "TransferRinging" current-state)
                   (cond
                     (#{"failed" "no-answer" "busy"} dialstatus) {:ccapi_cause cause}
                     (= "user-hangup" cause) (enter-state-ticket call status nil)
                     :else {})
                   (and (= "AcdTransferred" current-state)
                        (not (and (overflow-cause)
                                  (= "xml-hangup" cause)))) {}
                   (= "user-hangup" cause) {:cause "CALLER_HANG_UP"}
                   :else {:cause "IVR_HANG_UP"
                          :ccapi_cause cause})
    {}))


(defn leave-state-ticket
  [{{current-state :current
     {:keys [overflow-cause sda]} :info
     {:strs [bridgecause bridgeduration dialcause dialstatus]} :dial-status} :state
    :as call}
   {:strs [cause] :as status}
   next-state]
  (condp = current-state
    "AcdTransferred" (if (and overflow-cause (= "xml-hangup" cause))
                       {:acdcause "ACD_OVERFLOW"
                        :overflowcause overflow-cause}
                       {})
    "TransferRinging" (cond
                        (= "Transferred" next-state) {}
                        (= "Terminated" next-state) (cond
                                                      (#{"failed" "no-answer" "busy"} dialstatus)
                                                      (leave-state-ticket call status nil)
                                                      (= "user-hangup" cause)
                                                      {:ringingSda sda}
                                                      :else
                                                      {})
                        :else {:failedSda sda
                               :dialcause dialstatus
                               :ccapi_dialcause dialcause})
    "Transferred" {:sda sda
                   :bridgecause bridgecause
                   :bridgeduration bridgeduration}
    {}))


(defn emit-state-ticket
  [{:keys [state] :as call}
   {:keys [now next-state status] :as update}]
  (let [current-state (current-state call)
        {:keys [failed-sda overflow-cause queue sda]} (:info state)
        {:strs [bridgecause bridgeduration dialcause dialstatus]} (:dial-status state)
        duration (- now (:start-time state))
        base-ticket {:state current-state
                     :nextState next-state
                     :time now
                     :duration duration}]
    (match [current-state next-state]
           ["Created" "AcdTransferred"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ;; ["Created" "Created"]
           ;; {}
           ["Created" "InProgress"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ;; ["Created" "Transferred"]
           ;; {}
           ["Created" "TransferRinging"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ;; ["Created" "Terminated"]
           ;; {}


           ["InProgress" "AcdTransferred"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ;; ["InProgress" "Created"]
           ;; {}
           ;; ["InProgress" "InProgress"]
           ;; {}
           ;; ["InProgress" "Transferred"]
           ;; {}
           ["InProgress" "TransferRinging"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ["InProgress" "Terminated"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}


           ;; ["AcdTransferred" "AcdTransferred"]
           ;; {}
           ;; ["AcdTransferred" "Created"]
           ;; {}
           ["AcdTransferred" "InProgress"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ;; ["AcdTransferred" "Transferred"]
           ;; {}
           ["AcdTransferred" "TransferRinging"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ["AcdTransferred" "Terminated"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}


           ["TransferRinging" "AcdTransferred"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ;; ["TransferRinging" "Created"]
           ;; {}
           ["TransferRinging" "InProgress"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ["TransferRinging" "Transferred"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ["TransferRinging" "TransferRinging"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))}
           ["TransferRinging" "Terminated"]
           {:ivr.ticket/emit
            (merge base-ticket
                   (leave-state-ticket call status next-state)
                   (enter-state-ticket call status next-state))})))


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
