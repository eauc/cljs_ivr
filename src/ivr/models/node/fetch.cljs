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
   options]
  {:ivr.web/request
   {:method "POST"
    :url (str "/smartccivrservices/account/" account-id "/routingrule/" id_routing_rule "/eval")
    :on-success [::apply-routing-rule {:options options :node node}]
    :on-error [::error-routing-rule {:options options :node node}]}})


(defn- apply-routing-rule
  [{:keys [node options response]}]
  (let [{:keys [action-data call-id]} options
        var-name (:varname node)
        value (aget response "body")]
    (merge  {:ivr.call/action-data
             {:call-id call-id
              :data (assoc action-data var-name value)}}
            (node/go-to-next node options))))

(routes/reg-action
  ::apply-routing-rule
  apply-routing-rule)


(defn- error-routing-rule
  [{:keys [node options error]}]
  (let [{:keys [action-data call-id]} options
        var-name (:varname node)
        rule-id (:id_routing_rule node)]
    (log "warn" "routing rule" {:error error :id rule-id})
    (merge {:ivr.call/action-data
            {:call-id call-id
             :data (assoc action-data var-name "__FAILED__")}}
           (node/go-to-next node options))))

(routes/reg-action
  ::error-routing-rule
  error-routing-rule)


(defmethod node/leave-type "fetch"
  [node options]
  (node/go-to-next node options))
