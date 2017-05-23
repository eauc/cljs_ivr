(ns ivr.models.script
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.models.node.announcement]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))

(spec/def ::account-id
  string?)

(spec/def ::id
  string?)

(spec/def ::start
  keyword?)

(spec/def ::nodes
  (spec/coll-of :ivr.models.node/node :kind map?))

(spec/def ::script
  (spec/keys :req-un [::id ::account-id ::start ::nodes]))

(spec/fdef conform
           :args (spec/cat :script map?
                           :options (spec/keys :req-un [::account-id]))
           :ret ::script)
(defn conform [script {:keys [account-id]}]
  (-> script
      (assoc :account-id account-id)
      (update :start #(if (nil? %) nil (keyword (str %))))
      (update :nodes #(into {} (for [[k v] %] [k (node/conform v {:id (subs (str k) 1)
                                                                  :script-id (:id script)
                                                                  :account-id account-id})])))))

(spec/fdef start
           :args (spec/cat :script ::script :enter-node fn?)
           :ret map?)
(defn start [script enter-node]
  (let [start-index (:start script)
        start-node (get-in script [:nodes start-index])]
    (cond
      (nil? start-index) {:ivr.routes/response
                          (routes/error-response
                           {:status 500
                            :status_code "invalid_script"
                            :message "Invalid script - missing start index"
                            :cause script})}
      (nil? start-node) {:ivr.routes/response
                         (routes/error-response
                          {:status 500
                           :status_code "invalid_script"
                           :message "Invalid script - missing start node"
                           :cause script})}
      (not (spec/valid? :ivr.models.node/type
                        (:type start-node))) {:ivr.routes/response
                                              (routes/error-response
                                               {:status 500
                                                :status_code "invalid_node"
                                                :message "Invalid node - type"
                                                :cause start-node})}
      :else (enter-node start-node {}))))

(re-frame/reg-event-fx
 ::start-route
 [routes/interceptor
  db/default-interceptors]
 (fn start-route [_ [_ {:keys [req]}]]
   (let [script (aget req "script")]
     (start script node/enter))))
