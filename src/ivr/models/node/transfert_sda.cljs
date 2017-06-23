(ns ivr.models.node.transfert-sda
  (:require [ivr.models.call :as call]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [re-frame.core :as re-frame]))

(defn- conform-case-no-answer
  [case]
  (cond-> case
    (contains? case "noanswer") (-> (#(assoc % "no-answer" (get case "noanswer")))
                                    (dissoc "noanswer"))))


(defn- conform-case
  [node]
  (cond-> node
    (contains? node "case") (update "case" conform-case-no-answer)))


(defmethod node/conform-type "transfersda"
  [node]
  (conform-case node))


(defmethod node/enter-type "transfersda"
  [{:strs [account_id] :as node}
   {{:keys [store]} :deps}]
  (let [finally [::transfert-sda-with-config {:node node}]]
    {:ivr.web/request
     (store
       {:type :ivr.store/get-account
        :id account_id
        :on-success finally
        :on-error finally})}))


(defn transfert-sda-with-config
  [{:keys [config verbs] :as coeffects}
   {:keys [node account]}
   {:keys [params]}]
  (let [transfert-config (node/->transfert-config config account params)
        {:strs [dest id script_id]} node
        callback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script_id :node-id id})
        status-url (url/absolute [:v1 :status :dial]
                                 {:script-id script_id})
        call (get params "call")]
    {:dispatch-n [(call/update-sda call dest)]
     :ivr.routes/response
     (verbs
       [(merge {:type :ivr.verbs/dial-number
                :number dest
                :callbackurl callback-url
                :statusurl status-url}
               transfert-config)])}))

(node/reg-action
  ::transfert-sda-with-config
  [(re-frame/inject-cofx :ivr.config/cofx [:ivr :transfersda])]
  transfert-sda-with-config)

(defmethod node/leave-type "transfersda"
  [node {:keys [deps params]}]
  (let [dial-status (-> (get params "dialstatus")
                        (#{"no-answer" "busy" "completed"})
                        (or "other"))]
    (if (= "completed" dial-status)
      (let [{:keys [verbs]} deps]
        {:ivr.routes/response
         (verbs
           [{:type :ivr.verbs/hangup}])})
      (let [next (get-in node ["case" dial-status])]
        (node/go-to-next (assoc node "next" next) deps)))))
