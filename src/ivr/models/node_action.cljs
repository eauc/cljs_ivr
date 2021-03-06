(ns ivr.models.node-action
  (:require [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.models.verbs :as verbs]
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
  [context {:keys [call-id current-action node response success?]}]
  (let [node-type (get node "type")
        verb (verbs/first-verb response)
        next-state (cond
                     (and (= :enter current-action)
                          (= "transferqueue" node-type)) "AcdTransferred"
                     (and (= :enter current-action)
                          (= "transfersda" node-type)
                          (= :Dial verb)) "TransferRinging"
                     (and (= "transferlist" node-type)
                          (= :Dial verb)) "TransferRinging"
                     (= "transferlist" node-type) nil
                     (= :enter current-action) "InProgress"
                     :else nil)
        change-state? (and success? next-state)
        state-update {:id call-id :next-state next-state}]
    (log "debug" "check-call-state"
         {:node-type node-type :verb verb :next-state next-state :change-state? change-state?})
    (cond-> context
      change-state? (update :effects add-dispatch
                            [:ivr.call/state state-update]))))


(defn- check-start-action
  [context {:keys [call-id current-action node success?]}]
  (let [node-action (get node "stat")
        start-action? (and success?
                           (= :enter current-action)
                           (not (nil? node-action)))]
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
        success? (and (not (nil? response)) (= 200 status))
        action-info {:call-id call-id :current-action current-action :node node :response response :success? success?}]
    (log "debug" "check-action"
         {:current-action current-action :node node :response response :success? success?})
    (-> context
        (check-start-action action-info)
        (check-call-state action-info))))


(def interceptor
  (re-frame/->interceptor
    :id :ivr.node/action
    :after check-action))
