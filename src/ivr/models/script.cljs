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

(routes/reg-action
 :ivr.script/resolve
 [(re-frame/inject-cofx :ivr.store/cofx)]
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

(routes/reg-action
  ::resolve-success
 resolve-success)


(defn resolve-error [_ [_ {:keys [script-id error]}]]
  {:ivr.routes/response
   (routes/error-response
    {:status 404
     :status_code "script_not_found"
     :message "Script not found"
     :cause (assoc error :scriptid script-id)})})

(routes/reg-action
  ::resolve-error
 resolve-error)


(defn- enter-node-id [script node-id
                      {:keys [call enter-node store verbs] :as options}]
  (let [node (get-in script [:nodes node-id])
        action-data (:action-data call)
        call-id (get-in call [:info :id])]
    (if (nil? node)
      {:ivr.routes/response
       (routes/error-response
        {:status 500
         :status_code "invalid_script"
         :message "Invalid script - missing node"
         :cause script})}
      (enter-node node {:action-data action-data
                        :call-id call-id
                        :store store
                        :verbs verbs}))))


(defn start-route [coeffects
                   [_ {:keys [params] :as route}]]
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
                     (assoc coeffects :call call)))))

(routes/reg-action
 :ivr.script/start-route
 [(re-frame/inject-cofx :ivr.node/enter-cofx)
  (re-frame/inject-cofx :ivr.store/cofx)
  (re-frame/inject-cofx :ivr.verbs/cofx)]
 start-route)


(defn enter-node-route [coeffects
                        [_ {:keys [params] :as route}]]
  (let [{:keys [script call node_id]} params]
    (enter-node-id script (keyword node_id)
                   (assoc coeffects :call call))))

(routes/reg-action
 :ivr.script/enter-node-route
 [(re-frame/inject-cofx :ivr.node/enter-cofx)
  (re-frame/inject-cofx :ivr.store/cofx)
  (re-frame/inject-cofx :ivr.verbs/cofx)]
 enter-node-route)


(defn leave-node-route [{:keys [leave-node store verbs] :as coeffects}
                        [_ {:keys [params] :as route}]]
  (let [{:keys [script call node_id]} params
        action-data (:action-data call)
        call-id (get-in call [:info :id])
        node (get-in script [:nodes (keyword node_id)])]
    (cond
      (nil? node) {:ivr.routes/response
                   (routes/error-response
                    {:status 500
                     :status_code "invalid_script"
                     :message "Invalid script - missing node"
                     :cause script})}
      :else (leave-node node {:action-data action-data
                              :call-id call-id
                              :params params
                              :store store
                              :verbs verbs}))))

(routes/reg-action
 :ivr.script/leave-node-route
 [(re-frame/inject-cofx :ivr.node/leave-cofx)
  (re-frame/inject-cofx :ivr.store/cofx)
  (re-frame/inject-cofx :ivr.verbs/cofx)]
 leave-node-route)
