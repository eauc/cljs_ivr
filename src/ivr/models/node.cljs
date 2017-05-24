(ns ivr.models.node
  (:require [cljs.spec :as spec]
            [ivr.models.store :as store]
            [ivr.models.verbs :as verbs]
            [ivr.services.routes :as routes]
            [ivr.specs.node :as node-specs]))

(defn node-type [node]
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

(spec/fdef enter
           :args (spec/cat :node :ivr.node/node
                           :options :ivr.node/enter-options)
           :ret map?)
(defn enter [node {:keys [action-data call-id]}]
  (enter-type node {:action-data action-data
                    :call-id call-id
                    :store store/query
                    :verbs verbs/create}))
