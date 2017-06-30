(ns ivr.models.call-state
  (:require [cljs.core.match :refer-macros [match]]
            [ivr.libs.logger :as logger]
            [clojure.set :as set]))

(def log
  (logger/create "callState"))


(defn current-state
  [call]
  (get-in call [:state :current]))


(defmulti enter-ticket (fn [_ _ next-state] next-state))

(defmethod enter-ticket :default
  []
  {})


(defmulti leave-ticket #(current-state %1))

(defmethod leave-ticket :default
  []
  {})


(defmulti end-cause (fn [_ from] from))

(defmethod end-cause :default
  []
  nil)


(defmulti on-enter (fn [_ {:keys [to]}] to))

(defmethod on-enter :default
  []
  {})


(defmulti on-leave (fn [_ {:keys [from]}] from))

(defmethod on-leave :default
  []
  {})


(defn change-event
  [call {:keys [next-state now] :as update}]
  (let [id (get-in call [:info :id])
        current-state (current-state call)
        event {:id id :time now :from current-state :to next-state}]
    (if-not (= current-state next-state)
      {:dispatch-n [[:ivr.call/leave-state event]
                    [:ivr.call/enter-state event]]})))


(defn emit-ticket?
  [current-state next-state]
  (match [current-state next-state]
         [_ "Created"] false
         ["Created" "Terminated"] false
         ["InProgress" "InProgress"] false
         ["AcdTransferred" "AcdTransferred"] false
         [(nil :<< #{"TransferRinging"}) "Transferred"] false
         ["Transferred" (nil :<< #{"Terminated"})] false
         ["Terminated" _] false
         :else true))


(defn base-ticket
  [{{current-state :current
     start-time :start-time} :state :as call}
   {:keys [now next-state] :as update}]
  (let [duration (- now start-time)]
    (merge (set/rename-keys (:info call) {:id :callId
                                          :time :callTime
                                          :account-id :accountid
                                          :application-id :applicationid
                                          :script-id :scriptid})
           {:subject "CALL"
            :state current-state
            :nextState next-state
            :time now
            :duration duration})))


(defn emit-ticket
  [{{current-state :current
     {:keys [failed-sda overflow-cause sda]} :info
     {:strs [dialcause dialstatus]} :dial-status} :state
    :as call}
   {:keys [next-state status] :as update}]
  (let [{:strs [cause]} status
        base-ticket (base-ticket call update)]
    (if (emit-ticket? current-state next-state)
      (match [current-state next-state]
             ["AcdTransferred" "Terminated"]
             {:ivr.ticket/emit
              (if (and overflow-cause (= "xml-hangup" cause))
                (merge base-ticket
                       (leave-ticket call status next-state)
                       (enter-ticket call status next-state))
                base-ticket)}
             ["Transferred" "Terminated"]
             {:ivr.ticket/emit
              (merge base-ticket
                     (leave-ticket call status next-state))}
             ["TransferRinging" "TransferRinging"]
             {:ivr.ticket/emit
              (merge base-ticket
                     {:failedSda failed-sda
                      :dialcause dialstatus
                      :ccapi_dialcause dialcause}
                     (enter-ticket call status next-state))}
             ["TransferRinging" "Terminated"]
             {:ivr.ticket/emit
              (cond
                (#{"failed" "no-answer" "busy"} dialstatus)
                (merge base-ticket
                       (leave-ticket call status next-state)
                       {:cause "IVR_HANG_UP"
                        :ccapi_cause cause})
                (= "user-hangup" cause)
                (merge base-ticket
                       {:ringingSda sda}
                       (enter-ticket call status next-state))
                :else base-ticket)}
             :else
             {:ivr.ticket/emit
              (merge base-ticket
                     (leave-ticket call status next-state)
                     (enter-ticket call status next-state))}))))
