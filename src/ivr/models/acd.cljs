(ns ivr.models.acd
  (:require [clojure.set :as set]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.call :as call]
            [ivr.routes.url :as url]
            [ivr.services.routes.error :as routes-error]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "acd"))


(defmulti query :type)

(re-frame/reg-cofx
  :ivr.acd/cofx
  (fn acd-cofx [coeffects _]
    (assoc coeffects :acd query)))


(defmethod query :default
  [params]
  {:ivr.routes/response
   (routes-error/error-response
     {:status 500
      :status_code "invalid_acd_query"
      :message "Invalid ACD query - type"
      :cause params})})


(defn- enqueue-call-success
  [[event-name event-payload] response]
  (let [wait-sound (-> (aget response "body")
                       (get "waitSound"))]
    [event-name (assoc event-payload :wait-sound wait-sound)]))


(defmethod query :ivr.acd/enqueue-call
  [{:keys [call node_id script_id on-success on-error] :as params}]
  (let [fallback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script_id
                                    :node-id node_id})
        call-id (call/id call)
        call-time (call/created-time call)
        acd-params (-> params
                       (select-keys [:account_id :application_id :queue_id :to :from])
                       (merge {:call_id call-id
                               :callTime call-time
                               :ivr_fallback fallback-url}))]
    {:method "POST"
     :url (str "/smartccacdlink/call/" call-id "/enqueue")
     :data acd-params
     :on-success (partial enqueue-call-success on-success)
     :on-error on-error}))


(defmethod query :ivr.acd/update-call-status
  [{:keys [call-id] :as params}]
  {:method "POST"
   :url (str "/smartccacdlink/call/" call-id "/principal/status")
   :data (-> (dissoc params :type)
             (set/rename-keys {:account-id :account_id
                               :call-id :call_id}))
   :on-success [:ivr.acd/update-call-status-success params]
   :on-error [:ivr.acd/update-call-status-error params]})


(defn update-call-status-success
  [_ params]
  (log "info" "update call status ok" params)
  {})

(db/reg-event-fx
  :ivr.acd/update-call-status-success
  update-call-status-success)


(defn update-call-status-error
  [_ params]
  (log "error" "update call status error" params)
  {})

(db/reg-event-fx
  :ivr.acd/update-call-status-error
  update-call-status-error)
