(ns ivr.models.script
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.models.node.announcement]
            [ivr.models.store :as store]
            [ivr.services.routes :as routes]
            [ivr.specs.node]
            [ivr.specs.script]
            [re-frame.core :as re-frame]))


(defn- resolve-event [{:keys [store]} [_ {:keys [params]}]]
  (let [account-id (:account_id params)
        script-id (:script_id params)
        on-success [::resolve-success {:account-id account-id}]
        on-error [::resolve-error {:script-id script-id}]]
    {:ivr.web/request
     (store
      {:type :ivr.store/get-script
       :account-id account-id
       :script-id script-id
       :on-success on-success
       :on-error on-error})}))
(re-frame/reg-event-fx
 ::resolve
 [routes/interceptor
  db/default-interceptors
  (re-frame/inject-cofx :ivr.store/cofx)]
 resolve-event)


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


(defn resolve-success [_ [_ {:keys [account-id response]} {:keys [params]}]]
  (let [script (-> (aget response "body")
                   (conform {:account-id account-id}))]
    {:ivr.routes/params (assoc params :script script)
     :ivr.routes/next nil}))
(re-frame/reg-event-fx
 ::resolve-success
 [routes/interceptor
  db/default-interceptors]
 resolve-success)


(defn resolve-error [_ [_ {:keys [script-id error]}]]
  {:ivr.routes/response
   (routes/error-response
    {:status 404
     :status_code "script_not_found"
     :message "Script not found"
     :cause (assoc error :scriptid script-id)})})
(re-frame/reg-event-fx
 ::resolve-error
 [routes/interceptor
  db/default-interceptors]
 resolve-error)


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


(defn start-route [{:keys [enter-node]} [_ {:keys [params] :as route}]]
  (let [{:keys [script call]} params
        action-data (:action-data call)]
    (start script {:action-data action-data
                   :call-id (get-in call [:info :id])
                   :enter-node enter-node})))
(re-frame/reg-event-fx
 ::start-route
 [routes/interceptor
  db/default-interceptors
  (re-frame/inject-cofx :ivr.node/enter-cofx)]
 start-route)
