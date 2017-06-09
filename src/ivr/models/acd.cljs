(ns ivr.models.acd
  (:require [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))

(defmulti query :type)

(re-frame/reg-cofx
	:ivr.acd/cofx
	(fn acd-cofx [coeffects _]
		(assoc coeffects :acd query)))


(defmethod query :default
  [params]
  {:ivr.routes/response
   (routes/error-response
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
        call-id (get-in call [:info :id])
        call-time (get-in call [:info :time])
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
