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
   {{:keys [store]} :deps}]
  (let [finally [::transfert-sda-with-config {:node node}]]
    {:ivr.web/request
     (store
       {:type :ivr.store/get-account
        :id account-id
        :on-success finally
        :on-error finally})}))


(defn transfert-sda-with-config
  [{:keys [config verbs] :as coeffects}
   {:keys [node account]}
   {:keys [params]}]
  (let [transfert-config (node/->transfert-config config account params)
        {:keys [dest id script-id]} node
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
  transfert-sda-with-config)

(defmethod node/leave-type "transfersda"
  [node {:keys [deps params]}]
  (if (= "completed" (:dialstatus params))
    (let [{:keys [verbs]} deps]
      {:ivr.routes/response
       (verbs
         [{:type :ivr.verbs/hangup}])})
    (let [dialstatus (or (#{"no-answer" "busy"} (:dialstatus params))
                         "other")
          next (get-in node [:case (keyword dialstatus)])]
      (node/go-to-next (assoc node :next next) deps))))
