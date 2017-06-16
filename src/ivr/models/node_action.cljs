(ns ivr.models.node-action
  (:require [ivr.libs.logger :as logger]
            [ivr.services.routes.interceptor :as routes-interceptor]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "nodeAction"))


(defn- dispatch-start-action
  [effects info]
  (-> effects
      (cond->
          (not (contains? effects :dispatch-n)) (assoc :dispatch-n []))
      (update :dispatch-n conj [:ivr.call/start-action info])))


(defn- check-node-enter
  [context]
  (let [route (routes-interceptor/get-context-route context)
        params (-> (:req route)
                   (aget "ivr-params"))
        call-id (get-in params ["call" :info :id])
        node (get params "node")
        node-action (get node "stat")
        action (get params "action")
        response (get-in context [:effects :ivr.routes/response])
        status (or (:status response) 200)
        enter-success (and (not (nil? node-action))
                           (not (nil? response)) (= 200 status)
                           (= :enter action))]
    (log "debug" "check-action-response"
         {:action action :node node :response response :enter-success enter-success})
    (cond-> context
       enter-success (update :effects dispatch-start-action
                             {:call-id call-id :action node-action}))))

(def interceptor
  (re-frame/->interceptor
    :id :ivr.node/check-node-enter
    :after check-node-enter))
