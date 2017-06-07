(ns ivr.models.node.transfert-queue
  (:require [clojure.walk :as walk]
            [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.services.routes :as routes]))

(def log
  (logger/create "node.transfert-queue"))


(defmethod node/conform-type "transferqueue"
  [node]
  node)


(defmethod node/enter-type "transferqueue"
  [{:strs [id script_id queue] :as node}
   {:keys [call deps params] :as context}]
  (let [{:keys [acd]} deps
        acd-params
        (-> params
            (select-keys ["account_id" "application_id" "to" "from"])
            (walk/keywordize-keys)
            (merge {:type :ivr.acd/enqueue-call
                    :call call
                    :node_id id
                    :script_id script_id
                    :queue_id queue
                    :on-success [::play-waiting-sound {:node node}]
                    :on-error [::error-acd-enqueue {:node node}]}))]
    {:ivr.web/request
     (acd acd-params)}))


(defn- play-waiting-sound
  [{:keys [verbs]} {:keys [wait-sound]}]
  {:ivr.routes/response
   (verbs
     [{:type :ivr.verbs/loop-play
       :path (str "/cloudstore/file/" wait-sound)}])})

(routes/reg-action
  ::play-waiting-sound
  play-waiting-sound)


(defn- error-acd-enqueue
  [{:keys [verbs]} {:keys [error node]}]
  (log "error" "enqueue ACD" {:error error :node node})
  {:ivr.routes/response
   (verbs
     [{:type :ivr.verbs/hangup}])})

(routes/reg-action
  ::error-acd-enqueue
  error-acd-enqueue)

(defmethod node/leave-type "transferqueue"
  [node {:keys [deps]}]
  (node/go-to-next node deps))
