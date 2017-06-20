(ns ivr.models.script
  (:require [ivr.libs.logger :as logger]
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
            [ivr.services.routes :as routes]
            [ivr.services.routes.error :as routes-error]
            [re-frame.core :as re-frame]))


(def log
  (logger/create "script"))


(defn- resolve-script-event
  [{:keys [store]} {:keys [params]}]
  (let [account-id (get params "account_id")
        script-id (get params "script_id")
        on-success [::resolve-script-success {:account-id account-id}]
        on-error [::resolve-script-error {:script-id script-id}]]
    {:ivr.web/request
     (store
       {:type :ivr.store/get-script
        :account-id account-id
        :script-id script-id
        :on-success on-success
        :on-error on-error})}))

(routes/reg-action
  :ivr.script/resolve-script
  [(re-frame/inject-cofx :ivr.store/cofx)]
  resolve-script-event)


(defn conform [script {:keys [account-id]}]
  (-> script
      (assoc "account_id" account-id)
      (cond->
          (contains? script "start") (update "start" str))
      (update "nodes" #(into {} (for [[k v] %]
                                  [k (node/conform v {:id k
                                                      :script-id (get script "id")
                                                      :account-id account-id})])))))


(defn resolve-script-success
  [_ {:keys [account-id script]} {:keys [params]}]
  (let [script (conform script {:account-id account-id})]
    {:ivr.routes/params (assoc params "script" script)
     :ivr.routes/next nil}))

(routes/reg-action
  ::resolve-script-success
  resolve-script-success)


(defn resolve-script-error
  [_ {:keys [script-id error]}]
  {:ivr.routes/response
   (routes-error/error-response
     {:status 404
      :status_code "script_not_found"
      :message "Script not found"
      :cause (assoc error :scriptid script-id)})})

(routes/reg-action
  ::resolve-script-error
  resolve-script-error)


(defn- resolve-node-id
  [script node-id action params]
  (let [node (get-in script ["nodes" node-id])]
    (if (nil? node)
      {:ivr.routes/response
       (routes-error/error-response
         {:status 500
          :status_code "invalid_script"
          :message "Invalid script - missing node"
          :cause script})}
      {:ivr.routes/params (assoc params "node" node "action" action)
       :ivr.routes/next nil})))


(defn resolve-start-node
  [_ {:keys [params] :as route}]
  (let [{:strs [script]} params
        start-index (get script "start")]
    (if (nil? start-index)
      {:ivr.routes/response
       (routes-error/error-response
         {:status 500
          :status_code "invalid_script"
          :message "Invalid script - missing start index"
          :cause script})}
      (resolve-node-id script start-index :enter params))))

(routes/reg-action
  :ivr.script/resolve-start-node
  resolve-start-node)


(defn resolve-node
  [_ {:keys [action]} {:keys [params] :as route}]
  (let [{:strs [script node_id]} params]
    (resolve-node-id script node_id action params)))

(routes/reg-action
  :ivr.script/resolve-node
  resolve-node)


(defn enter-node-route
  [{:keys [enter-node] :as deps}
   {:keys [params] :as route}]
  (let [{:strs [call node]} params]
    (enter-node node {:call call :deps deps :params params})))

(node/reg-action
  :ivr.script/enter-node-route
  [(re-frame/inject-cofx :ivr.node/enter-cofx)]
  enter-node-route)


(defn leave-node-route
  [{:keys [leave-node] :as deps}
   {:keys [params] :as route}]
  (let [{:strs [call node]} params]
    (leave-node node {:call call :deps deps :params params})))

(node/reg-action
  :ivr.script/leave-node-route
  [(re-frame/inject-cofx :ivr.node/leave-cofx)]
  leave-node-route)
