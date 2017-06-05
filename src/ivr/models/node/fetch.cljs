(ns ivr.models.node.fetch
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "node.fetch"))


(defmethod node/conform-type "fetch"
  [node]
  (-> node
      (update :varname keyword)))


(defmethod node/enter-type "fetch"
  [{:keys [account-id id_routing_rule varname] :as node}
   {:keys [call] :as context}]
  {:ivr.web/request
   {:method "POST"
    :url (str "/smartccivrservices/account/" account-id "/routingrule/" id_routing_rule "/eval")
    :on-success [::apply-routing-rule {:call call :node node}]
    :on-error [::error-routing-rule {:call call :node node}]}})


(defn- apply-routing-rule
  [deps {:keys [call node response]}]
  (let [{:keys [action-data]} call
        var-name (:varname node)
        value (aget response "body")
        new-data (assoc action-data var-name value)]
    (merge {:ivr.call/action-data (assoc call :action-data new-data)}
           (node/go-to-next node deps))))

(routes/reg-action
  ::apply-routing-rule
  apply-routing-rule)


(defn- error-routing-rule
  [deps {:keys [call node error]}]
  (let [{:keys [action-data]} call
        var-name (:varname node)
        new-data (assoc action-data var-name "__FAILED__")
        rule-id (:id_routing_rule node)]
    (log "warn" "routing rule" {:error error :id rule-id})
    (merge {:ivr.call/action-data (assoc call :action-data new-data)}
           (node/go-to-next node deps))))

(routes/reg-action
  ::error-routing-rule
  error-routing-rule)


(defmethod node/leave-type "fetch"
  [node {:keys [deps]}]
  (node/go-to-next node deps))
