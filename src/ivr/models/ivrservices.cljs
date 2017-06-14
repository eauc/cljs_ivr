(ns ivr.models.ivrservices
  (:require [ivr.services.routes.error :as routes-error]
            [re-frame.core :as re-frame]))

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
