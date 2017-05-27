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


(spec/fdef go-to-next
           :args (spec/cat :node :ivr.node/node
                           :options :ivr.node/options)
           :ret map?)
(defn- go-to-next
  [{:keys [script-id next] :as node}
   {:keys [verbs] :as options}]
  (if next
    {:ivr.routes/response
     (verbs [{:type :ivr.verbs/redirect
              :path (url/absolute
                     [:v1 :action :script-enter-node]
                     {:script-id script-id
                      :node-id (subs (str next) 1)})}])}
    {:ivr.routes/response
     (verbs [{:type :ivr.verbs/hangup}])}))


(defmethod node/enter-type "announcement"
  [{:keys [account-id script-id disabled soundname] :as node}
   {:keys [store] :as options}]
  (let [update-action-data (node/apply-preset node options)
        result (if disabled
                 (go-to-next node options)
                 {:ivr.web/request
                  (store
                   {:type :ivr.store/get-sound-by-name
                    :name soundname
                    :account-id account-id
                    :script-id script-id
                    :on-success [::play-sound (assoc options :node node)]})})]
    (merge update-action-data result)))



(spec/fdef play-sound
           :args (spec/cat :sound-url string?
                           :node :ivr.node.announcement/node
                           :options :ivr.node/verbs)
           :ret map?)
(defn play-sound [sound-url {:keys [id no_barge script-id]} verbs]
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
                :play [sound-url]}])})))

(re-frame/reg-event-fx
 ::play-sound
 [routes/interceptor
  db/default-interceptors]
 (fn play-sound-fx [_ [_ {:keys [node verbs sound-url]}]]
   (play-sound sound-url node verbs)))


(defmethod node/leave-type "announcement"
  [node options]
  (go-to-next node options))
