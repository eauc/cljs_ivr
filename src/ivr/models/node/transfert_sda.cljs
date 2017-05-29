(ns ivr.models.node.transfert-sda
  (:require [ivr.models.node :as node]
            [re-frame.core :as re-frame]
            [ivr.services.routes :as routes]
            [ivr.db :as db]
            [ivr.routes.url :as url]))


(defn- conform-case-no-answer
  [case]
  (-> (into {} (for [[k v] case] [k (keyword v)]))
      (assoc :no-answer (:noanswer case))
      (dissoc :noanswer)))


(defn- conform-case
  [node]
  (if (contains? node :case)
    (update node :case conform-case-no-answer)
    node))


(defmethod node/conform-type "transfersda"
  [node]
  (-> node
      conform-case))


(defmethod node/enter-type "transfersda"
  [{:keys [account-id] :as node}
   {:keys [store] :as options}]
  (let [finally [::trasnfert-sda-with-config
                 {:node node :options options}]]
    {:ivr.web/request
     (store
      {:type :ivr.store/get-account
       :id account-id
       :on-success finally
       :on-error finally})}))


(defn transfert-sda-with-config
  [{:keys [config] :as coeffects}
   [_ {:keys [node options response]
       :or {response #js {}}} {:keys [params]}]]
  (let [account (or {} (get response "body"))
        transfert-config (node/->transfert-config config account params)
        {:keys [dest id script-id]} node
        {:keys [verbs]} options
        callback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script-id :node-id id})
        status-url (url/absolute [:v1 :status :dial]
                                 {:script-id script-id})]
    {:ivr.web/response
     (verbs
      [(merge {:type :ivr.verbs/dial-number
               :number dest
               :callbackurl callback-url
               :statusurl status-url}
              transfert-config)])}))

(re-frame/reg-event-fx
 ::transfert-sda-with-config
 [routes/interceptor
  db/default-interceptors
  (re-frame/inject-cofx :ivr.config/cofx [:ivr :transfersda])]
 transfert-sda-with-config)
