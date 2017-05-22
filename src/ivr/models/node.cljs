(ns ivr.models.node
  (:require [cljs.spec :as spec]
            [ivr.libs.logger :as logger]
            [ivr.models.store :as store]
            ;; [ivr.models.verbs :as verbs]
            [ivr.services.routes :as routes]))

(def known-types
  #{"announcement"})

(spec/def ::account-id
  string?)

(spec/def ::script-id
  string?)

(spec/def ::next
  keyword?)

(spec/def ::type
  known-types)

(spec/def ::base-node
  (spec/keys :req-un [::account-id ::script-id ::type]
             :opt-un [::next]))

(spec/def ::node
  (spec/or :announcement :ivr.models.node.announcement/node))

(defn node-type [node]
  (or (known-types (:type node))
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

;; (spec/def ::store
;;   fn?)

;; (spec/def ::verbs
;;   fn?)

;; (spec/fdef enter-type
;;            :args (spec/cat :node ::node
;;                            :options (spec/keys :req-un [::verbs ::store]))
;;            :ret map?)
;; (defmulti enter-type #(node-type %))

;; (defmethod enter-type :unknown
;;   [node _]
;;   {:ivr.routes/response
;;    (routes/error-response
;;     {:status 500
;;      :status_code "invalid_node"
;;      :message "Invalid node - type"
;;      :cause node})})

;; (spec/fdef enter
;;            :args (spec/cat :node ::node)
;;            :ret map?)
;; (defn enter [node]
;;   (enter-type node {:store store/query
;;                     :verbs verbs/create}))
