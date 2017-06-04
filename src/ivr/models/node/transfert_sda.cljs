(ns ivr.models.node.transfert-sda
  (:require [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))


(defn- conform-case-no-answer
  [case]
  (-> (into {} (for [[k v] case] [k (keyword v)]))
      (#(assoc % :no-answer (:noanswer %)))
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
  (let [finally [::transfert-sda-with-config
                 {:node node :options options}]]
    {:ivr.web/request
     (store
       {:type :ivr.store/get-account
        :id account-id
        :on-success finally
        :on-error finally})}))


(defn transfert-sda-with-config
  [{:keys [config] :as coeffects}
   {:keys [node options response]
    :or {response #js {}}}
   {:keys [params]}]
  (let [account (or (aget response "body") {})
        transfert-config (node/->transfert-config config account params)
        {:keys [dest id script-id]} node
        {:keys [verbs]} options
        callback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script-id :node-id id})
        status-url (url/absolute [:v1 :status :dial]
                                 {:script-id script-id})]
    {:ivr.routes/response
     (verbs
       [(merge {:type :ivr.verbs/dial-number
                :number dest
                :callbackurl callback-url
                :statusurl status-url}
               transfert-config)])}))

(routes/reg-action
  ::transfert-sda-with-config
  [(re-frame/inject-cofx :ivr.config/cofx [:ivr :transfersda])]
  transfert-sda-with-config
  {:with-cofx? true})

(defmethod node/leave-type "transfersda"
  [node
   {:keys [params verbs] :as options}]
  (if (= "completed" (:dialstatus params))
    {:ivr.routes/response
     (verbs
       [{:type :ivr.verbs/hangup}])}
    (let [dialstatus (or (#{"no-answer" "busy"} (:dialstatus params))
                         "other")
          next (get-in node [:case (keyword dialstatus)])]
      (node/go-to-next (assoc node :next next) options))))
