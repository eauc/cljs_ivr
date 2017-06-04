(ns ivr.models.node.transfert-queue
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))

(def log
  (logger/create "node.transfert-queue"))


(defmethod node/conform-type "transferqueue"
  [node]
  node)


(defmethod node/enter-type "transferqueue"
  [{:keys [id script-id queue] :as node}
   {:keys [call-id call-time params] :as options}]
  (let [fallback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script-id
                                    :node-id id})
        acd-params (-> params
                       (select-keys [:account_id :application_id :call_id :to :from])
                       (merge {:queue_id queue
                               :ivr_fallback fallback-url
                               :callTime call-time}))]
    {:ivr.web/request
     {:method "POST"
      :url (str "/smartccacdlink/call/" call-id "/enqueue")
      :data acd-params
      :on-success [::play-waiting-sound {:options options}]
      :on-error [::error-acd-enqueue {:node node :options options}]}}))


(defn- play-waiting-sound
  [{:keys [response] {:keys [verbs]} :options}]
  (let [wait-sound (:waitSound (aget response "body"))]
    {:ivr.routes/response
     (verbs
       [{:type :ivr.verbs/loop-play
         :path (str "/cloudstore/file/" wait-sound)}])}))

(routes/reg-action
  ::play-waiting-sound
  play-waiting-sound)


(defn- error-acd-enqueue
  [{:keys [error node] {:keys [verbs]} :options}]
  (log "error" "enqueue ACD" {:error error :node node})
  {:ivr.routes/response
   (verbs
     [{:type :ivr.verbs/hangup}])})

(routes/reg-action
  ::error-acd-enqueue
  error-acd-enqueue)

(defmethod node/leave-type "transferqueue"
  [node options]
  (node/go-to-next node options))
