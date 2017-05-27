(ns ivr.models.node
  (:require [cljs.spec :as spec]
            [ivr.models.store :as store]
            [ivr.models.verbs :as verbs]
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


(defn conform-preset [node]
  (let [preset (:preset node)
        value (:value preset)
        varname (:varname preset)]
    (if (and preset
             (string? value)
             (string? varname) (not (empty? varname)))
      (if (re-find #"^\$" value)
        (assoc node :preset {:type :ivr.node.preset/copy
                             :from (keyword (subs value 1))
                             :to (keyword varname)})
        (assoc node :preset {:type :ivr.node.preset/set
                             :value value
                             :to (keyword varname)}))
      (dissoc node :preset))))


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


(spec/fdef apply-preset
           :args (spec/cat :node :ivr.script/node
                           :options :ivr.node/options)
           :ret map?)
(defn apply-preset [node {:keys [call-id action-data]}]
  (case (get-in node [:preset :type])
    :ivr.node.preset/copy
    (let [{:keys [from to]} (:preset node)
          value (get action-data from)]
      {:ivr.call/action-data {:call-id call-id
                              :data (assoc action-data to value)}})
    :ivr.node.preset/set
    (let [{:keys [to value]} (:preset node)]
      {:ivr.call/action-data {:call-id call-id
                              :data (assoc action-data to value)}})
    {}))
