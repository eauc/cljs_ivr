(ns ivr.models.script
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.models.node.announcement]
            [ivr.models.node.dtmf-catch]
            [ivr.models.node.fetch]
            [ivr.models.node.route]
            [ivr.models.node.smtp]
            [ivr.models.node.transfert-list]
            [ivr.models.node.transfert-queue]
            [ivr.models.node.transfert-sda]
            [ivr.models.node.voice-record]
            [ivr.models.store :as store]
            [ivr.services.routes :as routes]
            [ivr.specs.node]
            [ivr.specs.script]
            [re-frame.core :as re-frame]
            [ivr.libs.logger :as logger]))


(defn- resolve-event
  [{:keys [store]} {:keys [params]}]
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

(routes/reg-action
  :ivr.script/resolve
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


(defn resolve-success
  [_ {:keys [account-id response]} {:keys [params]}]
  (let [script (-> (aget response "body")
                   (conform {:account-id account-id}))]
    {:ivr.routes/params (assoc params :script script)
     :ivr.routes/next nil}))

(routes/reg-action
  ::resolve-success
  resolve-success)


(defn resolve-error
  [_ {:keys [script-id error]}]
  {:ivr.routes/response
   (routes/error-response
     {:status 404
      :status_code "script_not_found"
      :message "Script not found"
      :cause (assoc error :scriptid script-id)})})

(routes/reg-action
  ::resolve-error
  resolve-error)


(defn- enter-node-id
  [script node-id {:keys [deps] :as context}]
  (let [{:keys [enter-node]} deps
        node (get-in script [:nodes node-id])]
    (if (nil? node)
      {:ivr.routes/response
       (routes/error-response
         {:status 500
          :status_code "invalid_script"
          :message "Invalid script - missing node"
          :cause script})}
      (enter-node node context))))


(defn start-route
  [deps {:keys [params] :as route}]
  (let [{:keys [script call]} params
        start-index (:start script)]
    (if (nil? start-index)
      {:ivr.routes/response
       (routes/error-response
         {:status 500
          :status_code "invalid_script"
          :message "Invalid script - missing start index"
          :cause script})}
      (enter-node-id script start-index
                     {:call call :params params :deps deps}))))

(routes/reg-action
  :ivr.script/start-route
  [(re-frame/inject-cofx :ivr.node/enter-cofx)]
  start-route)


(defn enter-node-route
  [deps {:keys [params] :as route}]
  (let [{:keys [script call node_id]} params]
    (enter-node-id script (keyword node_id)
                   {:call call :params params :deps deps})))

(routes/reg-action
  :ivr.script/enter-node-route
  [(re-frame/inject-cofx :ivr.node/enter-cofx)]
  enter-node-route)


(defn leave-node-route
  [{:keys [leave-node] :as deps}
   {:keys [params] :as route}]
  (let [{:keys [script call node_id]} params
        node (get-in script [:nodes (keyword node_id)])]
    (if (nil? node)
      {:ivr.routes/response
       (routes/error-response
         {:status 500
          :status_code "invalid_script"
          :message "Invalid script - missing node"
          :cause script})}
      (leave-node node {:call call :params params :deps deps}))))

(routes/reg-action
  :ivr.script/leave-node-route
  [(re-frame/inject-cofx :ivr.node/leave-cofx)]
  leave-node-route)
