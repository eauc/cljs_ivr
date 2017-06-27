(ns ivr.models.node.announcement
  (:require [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.models.node-set :as node-set]
            [ivr.routes.url :as url]))

(def log
  (logger/create "announcementNode"))


(defmethod node/conform-type "announcement"
  [node]
  (node-set/conform-preset node))


(defmethod node/enter-type "announcement"
  [{:strs [account_id script_id disabled soundname] :as node}
   {:keys [call deps] :as context}]
  (let [{:keys [store]} deps
        update-action-data (node-set/apply-preset node call)
        result (if disabled
                 (node/go-to-next node deps)
                 {:ivr.web/request
                  (store
                    {:type :ivr.store/get-sound-by-name
                     :name soundname
                     :account-id account_id
                     :script-id script_id
                     :on-success [::play-sound {:node node}]})})]
    (merge update-action-data result)))


(defn play-sound
  [{:keys [verbs]} {:keys [sound-url node]}]
  (let [{:strs [id no_barge script_id]} node
        callback-url (url/absolute [:v1 :action :script-leave-node]
                                   {:script-id script_id
                                    :node-id id})
        play-verbs (if no_barge
                     [{:type :ivr.verbs/play :path sound-url}]
                     [{:type :ivr.verbs/gather
                       :numdigits 1
                       :timeout 1
                       :callbackurl callback-url
                       :play [sound-url]}])]
    {:ivr.routes/response
     (verbs (concat play-verbs
                    (node/go-to-next-verbs node)))}))

(node/reg-action
  ::play-sound
  play-sound)


(defmethod node/leave-type "announcement"
  [node {:keys [deps] :as context}]
  (node/go-to-next node deps))
