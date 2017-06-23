(ns ivr.models.node.transfert-list
  (:require [cljs.nodejs :as nodejs]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [re-frame.core :as re-frame]))

(defonce query-string (nodejs/require "querystring"))
(defonce xml-escape (nodejs/require "xml-escape"))


(def log
  (logger/create "node.transferlist"))


(defn- conform-failover
  [node]
  (-> node
      (assoc "next" (get node "failover"))
      (dissoc "failover")))


(defmethod node/conform-type "transferlist"
  [node]
  (conform-failover node))


(defn- play-transfert-list
  [{:strs [account_id] :as node}
   eval-list
   {:keys [store] :as deps}]
  (let [finally [::eval-list-with-config {:node node :eval-list eval-list}]]
    {:ivr.web/request
     (store
       {:type :ivr.store/get-account
        :id account_id
        :on-success finally
        :on-error finally})}))


(defmethod node/enter-type "transferlist"
  [node {:keys [deps]}]
  (play-transfert-list node {} deps))


(defn- eval-list-with-config
  [{:keys [config services] :as coeffects}
   {:keys [node eval-list account]}
   {:keys [params]}]
  (let [call-id (call/id (get params "call"))
        transfert-config (node/->transfert-config config account params)
        {account-id "account_id" list-id "dest"} node
        payload {:call-id call-id :node node :config transfert-config}]
    {:ivr.web/request
     (services
       {:type :ivr.services/eval-destination-list
        :account-id account-id
        :list-id list-id
        :data eval-list
        :on-success [::transfert-call-to-list payload]
        :on-error [::eval-list-error payload]})}))

(node/reg-action
  ::eval-list-with-config
  [(re-frame/inject-cofx :ivr.config/cofx [:ivr :transfersda])]
  eval-list-with-config)


(defn- prefix-eval-list-query
  [eval-list]
  (into {} (for [[k v] eval-list]
             [(str "_dstLst_" k) v])))


(defn- eval-list->callback-query
  [eval-list]
  (let [callback-params (-> (dissoc eval-list "sda")
                            prefix-eval-list-query)]
    (if-not (empty? callback-params)
      (->> (clj->js callback-params)
           (.stringify query-string)
           xml-escape
           (str "?"))
      "")))


(defn- callback-query->eval-list
  [query]
  (->> query
       (filter (fn [[k v]] (re-find #"^_dstLst_" k)))
       (map (fn [[k v]] [(nth (re-find #"^_dstLst_(.*)$" k) 1) v]))
       (into {})))


(defn- transfert-call-to-list
  [{:keys [db verbs]} {:keys [call-id config node list-value]}]
  (let [{:strs [id script_id]} node
        callback-query (eval-list->callback-query list-value)
        callback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script_id
                                    :node-id id})
        status-url (url/absolute [:v1 :status :dial]
                                 {:script-id script_id})
        sda (get list-value "sda")
        call (call/db-call db call-id)]
    {:dispatch-n [(call/update-sda call sda)]
     :ivr.routes/response
     (verbs
       [(merge {:type :ivr.verbs/dial-number
                :number sda
                :callbackurl (str callback-url callback-query)
                :statusurl status-url}
               config)])}))

(node/reg-action
  ::transfert-call-to-list
  transfert-call-to-list)


(defn- eval-list-error
  [deps {:keys [node error]}]
  (log "error" "eval-list"
       {:error error :node node})
  (node/go-to-next node deps))

(node/reg-action
  ::eval-list-error
  eval-list-error)


(defmethod node/leave-type "transferlist"
  [node {:keys [deps params]}]
  (if (= "completed" (get params "dialstatus"))
    (let [{:keys [verbs]} deps]
      {:ivr.routes/response
       (verbs
         [{:type :ivr.verbs/hangup}])})
    (let [eval-list (callback-query->eval-list params)]
      (play-transfert-list node eval-list deps))))
