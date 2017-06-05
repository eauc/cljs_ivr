(ns ivr.models.node.voice-record
  (:require [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))


(defn- conform-case-validate
  [case]
  (if (contains? case :validate)
    (let [validate (:validate case)]
      (if (string? validate)
        (assoc case :validate {:next (keyword validate)})
        (-> case
            (update :validate node/conform-set :set)
            (update-in [:validate :next] keyword))))
    case))


(defn- conform-case
  [case]
  (-> case
      conform-case-validate
      (update :cancel keyword)))


(defmethod node/conform-type "voicerecord"
  [node]
  (-> node
      (update :varname keyword)
      (#(if (contains? % :case)
          (update % :case conform-case)
          %))))


(defmethod node/enter-type "voicerecord"
  [node context]
  {:ivr.routes/dispatch
   [::record-with-config {:node node}]})


(defn- record-with-config
  [{:keys [config verbs]}
   {:keys [node]}]
  (let [{:keys [id script-id validateKey cancelKey]} node
        callback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script-id :node-id id})]
    {:ivr.routes/response
     (verbs
       [{:type :ivr.verbs/record
         :maxlength (:maxlength config)
         :finishonkey (str validateKey cancelKey)
         :callbackurl callback-url}])}))

(routes/reg-action
  ::record-with-config
  [(re-frame/inject-cofx :ivr.config/cofx [:ivr :voicerecord])]
  record-with-config)


(defn- record-canceled?
  [{:keys [cancelKey] :as node}
   {:keys [record_cause record_digits]
    :or {record_digits ""}
    :as params}]
  (and (= "digit-a" record_cause)
       (re-find (re-pattern (str cancelKey "$")) record_digits)))


(defn- cancel-record
  [{:keys [case] :as node}
   {:keys [deps] :as context}]
  (let [next (:cancel case)]
    (node/go-to-next (assoc node :next next) deps)))


(defn- validate-record
  [{:keys [varname case] :as node}
   {:keys [call deps params] :as context}]
  (let [new-data (-> (:action-data call)
                     (assoc varname (:record_url params))
                     (node/apply-data-set (get-in case [:validate :set])))
        update-data {:ivr.call/action-data (assoc call :action-data new-data)}
        next (get-in case [:validate :next])]
    (merge update-data
           (node/go-to-next (assoc node :next next) deps))))


(defmethod node/leave-type "voicerecord"
  [node
   {:keys [params] :as context}]
  (if (record-canceled? node params)
    (cancel-record node context)
    (validate-record node context)))
