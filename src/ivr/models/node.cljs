(ns ivr.models.node
  (:require [clojure.walk :as walk]
            [ivr.libs.logger :as logger]
            [ivr.models.acd]
            [ivr.models.ivrservices]
            [ivr.models.node-action :as node-action]
            [ivr.models.store]
            [ivr.models.verbs]
            [ivr.services.routes :as routes]
            [ivr.routes.url :as url]
            [ivr.services.routes.error :as routes-error]
            [re-frame.core :as re-frame]))


(def log
  (logger/create "node"))


(def default-interceptors
  [(re-frame/inject-cofx :ivr.acd/cofx)
   (re-frame/inject-cofx :ivr.services/cofx)
   (re-frame/inject-cofx :ivr.store/cofx)
   (re-frame/inject-cofx :ivr.verbs/cofx)
   node-action/interceptor])


(defn reg-action
  ([id interceptors handler]
   (routes/reg-action
     id
     (concat default-interceptors interceptors)
     handler))
  ([id handler]
   (reg-action id [] handler)))


(defn- node-type
  [node]
  (get node "type"))


(defmulti conform-type node-type)


(defmethod conform-type :default
  [node]
  (log "warn" "conform-unknown" node))


(defn conform
  [node {:keys [id account-id script-id]}]
  (cond-> node
    (map? node) (-> (merge {"id" id
                            "account_id" account-id
                            "script_id" script-id})
                    (conform-type))))


(defmulti enter-type #(node-type %))


(defmethod enter-type :default
  [node _]
  {:ivr.routes/response
   (routes-error/error-response
     {:status 500
      :status_code "invalid_node"
      :message "Invalid node - type"
      :cause node})})

(re-frame/reg-cofx
  :ivr.node/enter-cofx
  (fn enter-cofx [coeffects _]
    (assoc coeffects :enter-node enter-type)))


(defmulti leave-type #(node-type %))


(defmethod leave-type :default
  [node _]
  {:ivr.routes/response
   (routes-error/error-response
     {:status 500
      :status_code "invalid_node"
      :message "Invalid node - type"
      :cause node})})

(re-frame/reg-cofx
  :ivr.node/leave-cofx
  (fn leave-cofx [coeffects _]
    (assoc coeffects :leave-node leave-type)))


(defn go-to-next-verbs
  [{:strs [script_id next] :as node}]
  (if next
    [{:type :ivr.verbs/redirect
      :path (url/absolute
              [:v1 :action :script-enter-node]
              {:script-id script_id
               :node-id next})}]
    [{:type :ivr.verbs/hangup}]))


(defn go-to-next
  [node {:keys [verbs] :as deps}]
  {:ivr.routes/response
   (verbs (go-to-next-verbs node))})


(defn ->transfert-config
  [ivr-config account params]
  (let [config (merge {:fromSda "CALLEE"
                       :ringingTimeoutSec 10}
                      ivr-config
                      (walk/keywordize-keys account))
        from (or (if (= "CALLER" (:fromSda config))
                   (get params "from")
                   (get params "to"))
                 "sip:anonymous@anonymous.invalid")
        record-enabled (if (contains? account "record_enabled")
                         (get account "record_enabled")
                         false)
        waiting-url (str "/smartccivr/twimlets/loopPlay/" (:ringing_tone config))]
    {:from from
     :timeout (:ringingTimeoutSec config)
     :record record-enabled
     :waitingurl waiting-url}))
