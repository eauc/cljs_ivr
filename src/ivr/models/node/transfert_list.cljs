(ns ivr.models.node.transfert-list
  (:require [cljs.nodejs :as nodejs]
            [clojure.walk :as walk]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))


(defonce query-string (nodejs/require "querystring"))
(defonce xml-escape (nodejs/require "xml-escape"))


(def log
  (logger/create "node.transferlist"))


(defn- conform-failover
  [node]
  (-> node
      (assoc :next (keyword (:failover node)))
      (dissoc :failover)))


(defmethod node/conform-type "transferlist"
  [node]
  (-> node
      conform-failover))


(defn- play-transfert-list
  [{:keys [account-id] :as node}
   {:keys [store] :as options}]
  (let [finally [::eval-list-with-config
                 {:node node :options options}]]
    {:ivr.web/request
     (store
      {:type :ivr.store/get-account
       :id account-id
       :on-success finally
       :on-error finally})}))


(defmethod node/enter-type "transferlist"
  [node options]
  (play-transfert-list node options))


(defn- ->transfert-config
  [config account params]
  (let [config (merge {:fromSda "CALLEE"
                       :ringingTimeoutSec 10}
                      config
                      account)
        from (or (if (= "CALLER" (:fromSda config))
                   (:from params)
                   (:to params))
                 "sip:anonymous@anonymous.invalid")
        record-enabled (if (contains? account :record_enabled)
                         (:record_enabled account)
                         false)
        waiting-url (str "/smartccivr/twimlets/loopPlay/" (:ringing_tone config))]
    {:from from
     :timeout (:ringingTimeoutSec config)
     :record record-enabled
     :waitingurl waiting-url}))


(defn- eval-list-with-config
  [{:keys [config] :as coeffects}
   [_ {:keys [node options response]
       :or {response #js {}}} {:keys [params]}]]
  (let [account (or (aget response "body") {})
        transfert-config (->transfert-config config account params)
        {account-id :account-id list-id :dest} node
        payload {:node node
                 :options options
                 :config transfert-config}]
    {:ivr.web/request
     {:method "POST"
      :url (str "/smartccivrservices/account/" account-id "/destinationlist/" list-id "/eval")
      :data (or (:eval-list options) {})
      :on-success [::transfert-call-to-list payload]
      :on-error [::eval-list-error payload]}}))

(re-frame/reg-event-fx
 ::eval-list-with-config
 [routes/interceptor
  db/default-interceptors
  (re-frame/inject-cofx :ivr.config/cofx [:ivr :transfersda])]
 eval-list-with-config)


(defn- prefix-eval-list-query
  [eval-list]
  (into {} (for [[k v] eval-list]
             [(str "_dstLst_" (subs (str k) 1)) v])))


(defn- eval-list->callback-query
  [eval-list]
  (let [callback-params (-> (dissoc eval-list :sda)
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
       (walk/stringify-keys)
       (filter (fn [[k v]] (re-find #"^_dstLst_" k)))
       (map (fn [[k v]] [(nth (re-find #"^_dstLst_(.*)$" k) 1) v]))
       (into {})
       (walk/keywordize-keys)))


(defn- transfert-call-to-list
  [_ [_ {:keys [config node options response]}]]
  (let [{:keys [id script-id]} node
        {:keys [verbs]} options
        eval-list (aget response "body")
        callback-query (eval-list->callback-query eval-list)
        callback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script-id
                                    :node-id id})
        status-url (url/absolute [:v1 :status :dial]
                                 {:script-id script-id})]
    {:ivr.routes/response
     (verbs
      [(merge {:type :ivr.verbs/dial-number
               :number (:sda eval-list)
               :callbackurl (str callback-url callback-query)
               :statusurl status-url}
              config)])}))

(re-frame/reg-event-fx
 ::transfert-call-to-list
 [routes/interceptor
  db/default-interceptors]
 transfert-call-to-list)


(defn- eval-list-error
  [_ [_ {:keys [node options error]}]]
  (log "error" "eval-list" {:error error
                            :node node})
  (node/go-to-next node options))

(re-frame/reg-event-fx
 ::eval-list-error
 [routes/interceptor
  db/default-interceptors]
 eval-list-error)


(defmethod node/leave-type "transferlist"
  [node {:keys [params verbs] :as options}]
  (if (= "completed" (:dialstatus params))
    {:ivr.routes/response
     (verbs
      [{:type :ivr.verbs/hangup}])}
    (let [eval-list (callback-query->eval-list params)]
      (play-transfert-list node (assoc options :eval-list eval-list)))))
