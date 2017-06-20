(ns ivr.models.node.voice-record
  (:require [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.models.node-set :as node-set]
            [ivr.routes.url :as url]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "voiceRecordNode"))


(defn conform-case-validate
  [case]
  (let [validate (get case "validate")]
    (if (string? validate)
      (assoc case "validate" {"next" validate})
      (update case "validate" node-set/conform-set "set"))))


(defn- conform-case
  [case]
  (cond-> case
    (contains? case "validate") conform-case-validate))


(defmethod node/conform-type "voicerecord"
  [node]
  (cond-> node
    (contains? node "case") (update "case" conform-case)
    (contains? node "validateKey") (-> (assoc "validate_key" (get node "validateKey"))
                                       (dissoc "validateKey"))
    (contains? node "cancelKey") (-> (assoc "cancel_key" (get node "cancelKey"))
                                     (dissoc "cancelKey"))))


(defmethod node/enter-type "voicerecord"
  [node context]
  {:ivr.routes/dispatch
   [::record-with-config {:node node}]})


(defn- record-with-config
  [{:keys [config verbs]}
   {:keys [node]}]
  (let [{:strs [id script_id validate_key cancel_key]} node
        callback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script_id :node-id id})]
    {:ivr.routes/response
     (verbs
       [{:type :ivr.verbs/record
         :maxlength (:maxlength config)
         :finishonkey (str validate_key cancel_key)
         :callbackurl callback-url}])}))

(node/reg-action
  ::record-with-config
  [(re-frame/inject-cofx :ivr.config/cofx [:ivr :voicerecord])]
  record-with-config)


(defn- record-canceled?
  [{:strs [cancel_key] :as node}
   {:strs [record_cause record_digits]
    :or {record_digits ""}
    :as params}]
  (and (= "digit-a" record_cause)
       (re-find (re-pattern (str cancel_key "$")) record_digits)))


(defn- cancel-record
  [{:strs [case] :as node}
   {:keys [deps] :as context}]
  (let [next (get case "cancel")]
    (node/go-to-next (assoc node "next" next) deps)))


(defn- validate-record
  [{:strs [varname case] :as node}
   {:keys [call deps params] :as context}]
  (let [set-record-url [(-> params
                            (get "record_url")
                            (node-set/->SetEntry varname))]
        set-validate (get-in case ["validate" "set"])
        set (concat set-record-url set-validate)
        update-data (node-set/apply-set set call)
        next (get-in case ["validate" "next"])
        result (node/go-to-next (assoc node "next" next) deps)]
    (merge update-data result)))


(defmethod node/leave-type "voicerecord"
  [node
   {:keys [params] :as context}]
  (if (record-canceled? node params)
    (cancel-record node context)
    (validate-record node context)))
