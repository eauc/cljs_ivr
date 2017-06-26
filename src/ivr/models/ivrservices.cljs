(ns ivr.models.ivrservices
  (:require [clojure.set :as set]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.services.routes.error :as routes-error]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "ivrServices"))


(defmulti query :type)

(re-frame/reg-cofx
  :ivr.services/cofx
  (fn ivrservices-cofx
    [coeffects]
    (assoc coeffects :services query)))


(defmethod query :default
  [params]
  {:ivr.routes/response
   (routes-error/error-response
    {:status 500
     :status_code "invalid_services_query"
     :message "Invalid IVR Services query - type"
     :cause params})})


(defn- eval-routing-rule-success
  [[event-name event-payload] response]
  (let [value (aget response "body")]
    [event-name (assoc event-payload :route-value value)]))


(defmethod query :ivr.services/eval-routing-rule
  [{:keys [account-id route-id on-success on-error] :as params}]
  {:method "POST"
   :url (str "/smartccivrservices/account/" account-id "/routingrule/" route-id "/eval")
   :on-success (partial eval-routing-rule-success on-success)
   :on-error on-error})


(defmethod query :ivr.services/send-mail
  [{:keys [account-id context options on-success on-error] :as params}]
  {:method "POST"
   :url (str "/smartccivrservices/account/" account-id "/mail")
   :data {:context context
          :mailOptions options}
   :on-success on-success
   :on-error on-error})


(defn- eval-destination-list-success
  [[event-name event-payload] response]
  (let [value (aget response "body")]
    [event-name (assoc event-payload :list-value value)]))


(defmethod query :ivr.services/eval-destination-list
  [{:keys [account-id list-id data on-success on-error]
    :or {data {}} :as params}]
  {:method "POST"
   :url (str "/smartccivrservices/account/" account-id "/destinationlist/" list-id "/eval")
   :data data
   :on-success (partial eval-destination-list-success on-success)
   :on-error on-error})


(defmethod query :ivr.services/call-on-end
  [{:keys [account-id script-id] :as params}]
  {:method "POST"
   :url (str "/smartccivrservices/account/" account-id "/script/" script-id "/on-end")
   :data (-> (dissoc params :type)
             (set/rename-keys {:id :callid
                               :account-id :accountid
                               :application-id :applicationid
                               :script-id :scriptid
                               :time :callTime}))
   :on-success [:ivr.services/call-on-end-success params]
   :on-error [:ivr.services/call-on-end-error params]})


(defn call-on-end-success
  [_ params]
  (log "info" "call-on-end ok" params)
  {})

(db/reg-event-fx
  :ivr.services/call-on-end-success
  call-on-end-success)


(defn call-on-end-error
  [_ params]
  (log "error" "call-on-end error" params)
  {})

(db/reg-event-fx
  :ivr.services/call-on-end-error
  call-on-end-error)
