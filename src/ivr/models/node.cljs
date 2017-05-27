(ns ivr.models.node
  (:require [cljs.spec :as spec]
            [ivr.models.store :as store]
            [ivr.models.verbs :as verbs]
            [ivr.services.routes :as routes]
            [ivr.specs.node :as node-specs]
            [re-frame.core :as re-frame]))


(defn- node-type [node]
  (or (node-specs/known-types (:type node))
      :unknown))


(defmulti conform-type node-type)


(defmethod conform-type :unknown
  [node] node)


(defn conform [node {:keys [id account-id script-id]}]
  (if (map? node)
    (-> node
        (merge {:id id
                :account-id account-id
                :script-id script-id})
        (update :next keyword)
        (conform-type))
    node))


(spec/fdef enter-type
           :args (spec/cat :node :ivr.node/node
                           :options :ivr.node/enter-options)
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
           :args (spec/cat :node :ivr.node/node
                           :options :ivr.node/enter-options)
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
