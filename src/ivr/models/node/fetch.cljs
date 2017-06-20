(ns ivr.models.node.fetch
  (:require [ivr.libs.logger :as logger]
            [ivr.models.node :as node]))

(def log
  (logger/create "node.fetch"))


(defmethod node/conform-type "fetch"
  [node]
  node)


(defmethod node/enter-type "fetch"
  [{:strs [account_id id_routing_rule varname] :as node}
   {:keys [call deps] :as context}]
  (let [{:keys [services]} deps]
    {:ivr.web/request
     (services
       {:type :ivr.services/eval-routing-rule
        :account-id account_id
        :route-id id_routing_rule
        :on-success [::apply-routing-rule {:call call :node node}]
        :on-error [::error-routing-rule {:call call :node node}]})}))


(defn- apply-routing-rule
  [deps {:keys [call node route-value]}]
  (let [{:keys [action-data]} call
        var-name (get node "varname")
        new-data (assoc action-data var-name route-value)]
    (merge {:ivr.call/action-data (assoc call :action-data new-data)}
           (node/go-to-next node deps))))

(node/reg-action
  ::apply-routing-rule
  apply-routing-rule)


(defn- error-routing-rule
  [deps {:keys [call node error]}]
  (let [{:keys [action-data]} call
        var-name (get node "varname")
        new-data (assoc action-data var-name "__FAILED__")
        rule-id (get node "id_routing_rule")]
    (log "warn" "routing rule" {:error error :id rule-id})
    (merge {:ivr.call/action-data (assoc call :action-data new-data)}
           (node/go-to-next node deps))))

(node/reg-action
  ::error-routing-rule
  error-routing-rule)


(defmethod node/leave-type "fetch"
  [node {:keys [deps]}]
  (node/go-to-next node deps))
