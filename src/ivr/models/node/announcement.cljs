(ns ivr.models.node.announcement
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [ivr.specs.node.announcement]
            [re-frame.core :as re-frame]))


(defmethod node/conform-type "announcement"
  [node]
  (-> node
      node/conform-preset))


(defmethod node/enter-type "announcement"
  [{:keys [account-id script-id disabled soundname] :as node}
   {:keys [call deps] :as context}]
  (let [{:keys [store]} deps
        update-action-data (node/apply-preset node call)
        result (if disabled
                 (node/go-to-next node deps)
                 {:ivr.web/request
                  (store
                    {:type :ivr.store/get-sound-by-name
                     :name soundname
                     :account-id account-id
                     :script-id script-id
                     :on-success [::play-sound {:node node}]})})]
    (merge update-action-data result)))


;; (spec/fdef play-sound
;;            :args (spec/cat :sound-url string?
;;                            :node :ivr.node.announcement/node
;;                            :options :ivr.node/verbs)
;;            :ret map?)
(defn play-sound
  [{:keys [verbs]} {:keys [sound-url node]}]
  (let [{:keys [id no_barge script-id]} node]
    (if no_barge
      {:ivr.routes/response
       (verbs [{:type :ivr.verbs/play
                :path sound-url}])}
      (let [callback-url (url/absolute [:v1 :action :script-leave-node]
                                       {:script-id script-id
                                        :node-id id})]
        {:ivr.routes/response
         (verbs [{:type :ivr.verbs/gather
                  :numdigits 1
                  :timeout 1
                  :callbackurl callback-url
                  :play [sound-url]}])}))))

(routes/reg-action
  ::play-sound
  play-sound)


(defmethod node/leave-type "announcement"
  [node {:keys [deps] :as context}]
  (node/go-to-next node deps))
