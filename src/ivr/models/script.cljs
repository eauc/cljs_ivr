(ns ivr.models.script
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.models.node.announcement]
            [ivr.services.routes :as routes]
            [ivr.specs.node]
            [ivr.specs.script]
            [re-frame.core :as re-frame]))

(spec/fdef conform
           :args (spec/cat :script map?
                           :options (spec/keys :req-un [:ivr.script/account-id]))
           :ret :ivr.script/script)
(defn conform [script {:keys [account-id]}]
  (-> script
      (assoc :account-id account-id)
      (update :start #(if (nil? %) nil (keyword (str %))))
      (update :nodes #(into {} (for [[k v] %] [k (node/conform v {:id (subs (str k) 1)
                                                                  :script-id (:id script)
                                                                  :account-id account-id})])))))

(spec/fdef start
           :args (spec/cat :script :ivr.script/script
                           :options :ivr.script/start-options)
           :ret map?)
(defn start [script {:keys [call-id action-data enter-node]}]
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
      (not (spec/valid? :ivr.node/type
                        (:type start-node))) {:ivr.routes/response
                                              (routes/error-response
                                               {:status 500
                                                :status_code "invalid_node"
                                                :message "Invalid node - type"
                                                :cause start-node})}
      :else (enter-node start-node {:action-data action-data
                                    :call-id call-id}))))

(re-frame/reg-event-fx
 ::start-route
 [routes/interceptor
  db/default-interceptors]
 (fn start-route [{:keys [db]} [_ {:keys [req]}]]
   (let [script (aget req "script")
         call-id (aget req "query" "call_id")
         action-data (get-in db [:calls call-id :action-data])]
     (start script {:action-data action-data
                    :call-id call-id
                    :enter-node node/enter}))))
