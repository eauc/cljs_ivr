(ns ivr.models.node
  (:require [cljs.spec :as spec]
            [ivr.models.store :as store]
            [ivr.models.verbs :as verbs]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [ivr.specs.node :as node-specs]
            [ivr.specs.script]
            [re-frame.core :as re-frame]))


(defn- node-type [node]
  (or (node-specs/known-types (:type node))
      :unknown))


(spec/fdef conform-type
           :args (spec/cat :data map?)
           :ret :ivr.script/node)
(defmulti conform-type node-type)


(defmethod conform-type :unknown
  [node] node)


(spec/fdef conform
           :args (spec/cat :node map?
                           :options map?)
           :ret :ivr.script/node)
(defn conform [node {:keys [id account-id script-id]}]
  (if (map? node)
    (-> node
        (merge {:id id
                :account-id account-id
                :script-id script-id})
        (update :next keyword)
        (conform-type))
    node))


(defn conform-set [map key]
  (let [set (get map key)
        value (:value set)
        varname (:varname set)]
    (if (and set
             (string? value)
             (string? varname) (not (empty? varname)))
      (if (re-find #"^\$" value)
        (assoc map key {:type :ivr.node.preset/copy
                        :from (keyword (subs value 1))
                        :to (keyword varname)})
        (assoc map key {:type :ivr.node.preset/set
                        :value value
                        :to (keyword varname)}))
      (dissoc map key))))


(defn conform-preset [node]
  (conform-set node :preset))


(spec/fdef enter-type
           :args (spec/cat :node :ivr.script/node
                           :options :ivr.node/options)
           :ret map?)
(defmulti enter-type #(node-type %))


(defmethod enter-type :unknown
  [node _]
  {:ivr.routes/response
   (routes/error-response
    {:status 500
     :status_code "invalid_node"
     :message "Invalid node - type"
     :cause node})})

(re-frame/reg-cofx
 :ivr.node/enter-cofx
 (fn enter-cofx [coeffects _]
   (assoc coeffects :enter-node enter-type)))


(spec/fdef leave-type
           :args (spec/cat :node :ivr.script/node
                           :options :ivr.node/options)
           :ret map?)
(defmulti leave-type #(node-type %))


(defmethod leave-type :unknown
  [node _]
  {:ivr.routes/response
   (routes/error-response
    {:status 500
     :status_code "invalid_node"
     :message "Invalid node - type"
     :cause node})})

(re-frame/reg-cofx
 :ivr.node/leave-cofx
 (fn leave-cofx [coeffects _]
   (assoc coeffects :leave-node leave-type)))


(defn- apply-data-set
  [data set]
  (case (:type set)
    :ivr.node.preset/copy
    (let [{:keys [from to]} set
          value (get data from)]
      (assoc data to value))
    :ivr.node.preset/set
    (let [{:keys [to value]} set]
      (assoc data to value))
    data))


(spec/fdef apply-preset
           :args (spec/cat :node :ivr.script/node
                           :options :ivr.node/options)
           :ret map?)
(defn apply-preset
  [{:keys [preset] :as node}
   {:keys [call-id action-data]}]
  (let [new-data (apply-data-set action-data preset)]
    (if-not (= new-data action-data)
      {:ivr.call/action-data {:call-id call-id
                              :data new-data}}
      {})))


(spec/fdef go-to-next
           :args (spec/cat :node :ivr.script/node
                           :options :ivr.node/options)
           :ret map?)
(defn- go-to-next
  [{:keys [script-id next] :as node}
   {:keys [verbs] :as options}]
  (if next
    {:ivr.routes/response
     (verbs [{:type :ivr.verbs/redirect
              :path (url/absolute
                     [:v1 :action :script-enter-node]
                     {:script-id script-id
                      :node-id (subs (str next) 1)})}])}
    {:ivr.routes/response
     (verbs [{:type :ivr.verbs/hangup}])}))


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
