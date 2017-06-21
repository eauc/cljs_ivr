(ns ivr.models.node-action
  (:require [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.services.routes.interceptor :as routes-interceptor]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "nodeAction"))


(defn- add-dispatch
  [effects event]
  (-> effects
      (cond->
          (not (contains? effects :dispatch-n)) (assoc :dispatch-n []))
      (update :dispatch-n conj event)))


(defn- check-call-state
  [context {:keys [call-id enter-node? node response]}]
  (let [node-type (get node "type")
        verb (first (get-in response [:data 1]))
        next-state (cond
                     (and (= "transferqueue" node-type)
                          (= :Play (get verb 0))) "AcdTransferred"
                     (and (or (= "transfersda" node-type)
                              (= "transferlist" node-type))
                          (= :Dial (get verb 0))) "TransferRinging"
                     :else "InProgress")
        sda (get-in verb [2 1])
        queue (get node "queue")
        state-update (cond-> {:id call-id :next-state next-state}
                       (= "AcdTransferred" next-state) (assoc-in [:info :queue] queue)
                       (= "TransferRinging" next-state) (assoc-in [:info :sda] sda))]
    (log "debug" "check-call-state"
         {:node-type node-type :verb verb :next-state next-state :sda sda :queue queue})
    (cond-> context
      enter-node? (update :effects add-dispatch
                          [:ivr.call/state state-update]))))


(defn- check-start-action
  [context {:keys [call-id enter-node? node]}]
  (let [node-action (get node "stat")
        start-action? (and enter-node? (not (nil? node-action)))]
    (log "debug" "check-start-action"
         {:node-action node-action :start-action? start-action?})
    (cond-> context
      start-action? (update :effects add-dispatch
                            [:ivr.call/start-action {:call-id call-id :action node-action}]))))


(defn- check-action
  [context]
  (let [route (routes-interceptor/get-context-route context)
        params (-> (:req route) (aget "ivr-params"))
        call-id (-> params (get "call") call/id)
        current-action (get params "action")
        node (get params "node")
        response (get-in context [:effects :ivr.routes/response])
        status (or (:status response) 200)
        enter-node? (and (= :enter current-action)
                         (not (nil? response)) (= 200 status))
        action-info {:call-id call-id :enter-node? enter-node? :node node :response response}]
    (log "debug" "check-action"
         {:current-action current-action :node node :response response :enter-node? enter-node?})
    (-> context
        (check-start-action action-info)
        (check-call-state action-info))))


(def interceptor
  (re-frame/->interceptor
    :id :ivr.node/action
    :after check-action))
