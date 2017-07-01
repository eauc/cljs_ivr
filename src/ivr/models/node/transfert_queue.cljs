(ns ivr.models.node.transfert-queue
  (:require [clojure.walk :as walk]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.models.node :as node]))

(def log
  (logger/create "node.transfert-queue"))


(defmethod node/conform-type "transferqueue"
  [node]
  node)


(defmethod node/enter-type "transferqueue"
  [{:strs [id script_id queue] :as node}
   {:keys [call deps params] :as context}]
  (let [{:keys [acd]} deps
        call-id (call/id (get params "call"))
        acd-params
        (-> params
            (select-keys ["account_id" "application_id" "to" "from"])
            (walk/keywordize-keys)
            (merge {:type :ivr.acd/enqueue-call
                    :call call
                    :node_id id
                    :script_id script_id
                    :queue_id queue
                    :on-success [::play-waiting-sound {:call-id call-id :node node :queue-id queue}]
                    :on-error [::error-acd-enqueue {:node node}]}))]
    {:dispatch-n [[:ivr.call/state {:id call-id :info {:queue queue}}]]
     :ivr.web/request
     (acd acd-params)}))


(defn- play-waiting-sound
  [{:keys [verbs]} {:keys [call-id queue-id wait-sound]}]
  {:ivr.routes/response
   (verbs
     [{:type :ivr.verbs/loop-play
       :path (str "/cloudstore/file/" wait-sound)}])})

(node/reg-action
  ::play-waiting-sound
  play-waiting-sound)


(defn- error-acd-enqueue
  [{:keys [verbs]} {:keys [error node]}]
  (log "error" "enqueue ACD" {:error error :node node})
  {:ivr.routes/response
   (verbs
     [{:type :ivr.verbs/hangup}])})

(node/reg-action
  ::error-acd-enqueue
  error-acd-enqueue)

(defmethod node/leave-type "transferqueue"
  [{:strs [case] :as node}
   {:keys [deps params] :as context}]
  (let [{:strs [overflowcause]} params
        call-id (call/id (get params "call"))
        next (cond
               (= "NO_AGENT" overflowcause) (get case "noagent")
               (= "QUEUE_FULL" overflowcause) (get case "full")
               (= "QUEUE_TIMEOUT" overflowcause) (get case "timeout")
               :else nil)
        go-to-next (node/go-to-next (assoc node "next" next) deps)
        state-update {:dispatch-n [[:ivr.call/state {:id call-id :info {:overflow-cause overflowcause}}]]}]
    (merge state-update go-to-next)))
