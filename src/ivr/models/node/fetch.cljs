(ns ivr.models.node.fetch
  (:require [ivr.models.node :as node]
            [re-frame.core :as re-frame]
            [ivr.services.routes :as routes]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]))


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
    :on-success [::apply-routing-rule (assoc options :node node)]
    :on-error [::error-routing-rule (assoc options :node node)]}})


(defn- apply-routing-rule
  [_ [_ {:keys [action-data call-id node response] :as options}]]
  (let [var-name (:varname node)
        value (aget response "body")]
    (merge  {:ivr.call/action-data
             {:call-id call-id
              :data (assoc action-data var-name value)}}
            (node/go-to-next node (dissoc options :node)))))

(re-frame/reg-event-fx
 ::apply-routing-rule
 [routes/interceptor
  db/default-interceptors]
 apply-routing-rule)


(defn- error-routing-rule
  [_ [_ {:keys [call-id action-data error node] :as options}]]
  (let [var-name (:varname node)
        id-routing-rule (:id_routing_rule node)]
    (log "error" "routing rule" {:error error
                                 :id id-routing-rule})
    (merge {:ivr.call/action-data
            {:call-id call-id
             :data (assoc action-data var-name "__FAILED__")}}
           (node/go-to-next node (dissoc options :node)))))

(re-frame/reg-event-fx
 ::error-routing-rule
 [routes/interceptor
  db/default-interceptors]
 error-routing-rule)


(defmethod node/leave-type "fetch"
  [node options]
  (node/go-to-next node options))
