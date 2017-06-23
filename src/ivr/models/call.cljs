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


(defn state-ticket
  [state {:keys [next-state now] :as info}]
  (let [duration (- now (:start-time state))]
    (merge {:state (:current state)
            :nextState next-state
            :time now
            :duration duration}
           (dissoc info :next-state :now))))


(defn emit-state-ticket
  [{:keys [state] :as call}
   {:keys [now next-state info status] :as update}]
  (let [{:keys [failed-sda overflow-cause queue sda]} (:info state)
        {:strs [bridgecause bridgeduration dialcause dialstatus]} (:dial-status state)
        duration (- now (:start-time state))]
    (match [(:current state) next-state]
           ["Created" "AcdTransferred"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :queueid queue})}
           ["Created" "Created"]
           {}
           ["Created" "InProgress"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now})}
           ["Created" "Transferred"]
           {}
           ["Created" "TransferRinging"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :ringingSda sda})}
           ;; ["Created" "Terminated"]
           ;; {}


           ["InProgress" "AcdTransferred"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :queueid queue})}
           ;; ["InProgress" "Created"]
           ;; {}
           ;; ["InProgress" "InProgress"]
           ;; {}
           ;; ["InProgress" "Transferred"]
           ;; {}
           ["InProgress" "TransferRinging"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :ringingSda sda})}
           ["InProgress" "Terminated"]
           (let [cause (get status "cause")]
             (if (= "user-hangup" cause)
               {:ivr.ticket/emit
                (state-ticket state {:next-state next-state
                                     :now now
                                     :cause "CALLER_HANG_UP"})}
               {:ivr.ticket/emit
                (state-ticket state {:next-state next-state
                                     :now now
                                     :cause "IVR_HANG_UP"
                                     :ccapi_cause cause})}))


           ;; ["AcdTransferred" "AcdTransferred"]
           ;; {}
           ;; ["AcdTransferred" "Created"]
           ;; {}
           ["AcdTransferred" "InProgress"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :acdcause "ACD_OVERFLOW"
                                 :overflowcause overflow-cause})}
           ;; ["AcdTransferred" "Transferred"]
           ;; {}
           ["AcdTransferred" "TransferRinging"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :acdcause "ACD_OVERFLOW"
                                 :overflowcause overflow-cause
                                 :ringingSda sda})}
           ["AcdTransferred" "Terminated"]
           (let [cause (get status "cause")]
             (if (and overflow-cause (= "xml-hangup" cause))
               {:ivr.ticket/emit
                (state-ticket state {:next-state next-state
                                     :now now
                                     :acdcause "ACD_OVERFLOW"
                                     :overflowcause overflow-cause
                                     :cause "IVR_HANG_UP"
                                     :ccapi_cause cause})}
               {:ivr.ticket/emit
                (state-ticket state {:next-state next-state
                                     :now now})}))


           ["TransferRinging" "AcdTransferred"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :queueid queue
                                 :failedSda sda
                                 :dialcause dialstatus
                                 :ccapi_dialcause dialcause})}
           ;; ["TransferRinging" "Created"]
           ;; {}
           ["TransferRinging" "InProgress"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :failedSda sda
                                 :dialcause dialstatus
                                 :ccapi_dialcause dialcause})}
           ["TransferRinging" "Transferred"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :sda sda})}
           ["TransferRinging" "TransferRinging"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :ringingSda sda
                                 :failedSda failed-sda
                                 :dialcause dialstatus
                                 :ccapi_dialcause dialcause})}
           ["TransferRinging" "Terminated"]
           (let [cause (get status "cause")]
             (cond
               (#{"failed" "no-answer" "busy"} dialstatus)
               {:ivr.ticket/emit
                (state-ticket state {:next-state next-state
                                     :now now
                                     :failedSda sda
                                     :dialcause dialstatus
                                     :ccapi_cause cause
                                     :ccapi_dialcause dialcause})}
               (= "user-hangup" cause)
               {:ivr.ticket/emit
                (state-ticket state {:next-state next-state
                                     :now now
                                     :cause "CALLER_HANG_UP"
                                     :ringingSda sda})}
               :else
               {:ivr.ticket/emit
                (state-ticket state {:next-state next-state
                                     :now now})}))


           ;; ["Transferred" "AcdTransferred"]
           ;; {}
           ;; ["Transferred" "Created"]
           ;; {}
           ;; ["Transferred" "InProgress"]
           ;; {}
           ;; ["Transferred" "Transferred"]
           ;; {}
           ;; ["Transferred" "TransferRinging"]
           ;; {}
           ["Transferred" "Terminated"]
           {:ivr.ticket/emit
            (state-ticket state {:next-state next-state
                                 :now now
                                 :sda sda
                                 :bridgecause bridgecause
                                 :bridgeduration bridgeduration})}

           :else
           {})))


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
