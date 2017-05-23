(ns ivr.models.node.announcement
  (:require [cljs.spec :as spec]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [re-frame.core :as re-frame]
            [ivr.services.routes :as routes]
            [ivr.db :as db]))

(spec/def ::soundname
  string?)

(spec/def ::disabled
  boolean?)

(spec/def ::no_barge
  boolean?)

(spec/def ::node
  (spec/and :ivr.models.node/base-node
            (spec/keys :req-un [::soundname]
                       :opt-un [::disabled ::no_barge])))

(defmethod node/conform-type "announcement"
  [node] node)

(defmethod node/enter-type "announcement"
  [node {:keys [store verbs] :as options}]
  (let [{:keys [account-id script-id id next disabled soundname]} node]
    (if disabled
      (if next
        {:ivr.routes/response
         (verbs [{:type :ivr.verbs/redirect
                  :path (url/absolute
                          [:v1 :action :script-enter-node]
                          {:script-id script-id
                           :node-id (subs (str next) 1)})}])}
        {:ivr.routes/response
         (verbs [{:type :ivr.verbs/hangup}])})
      {:ivr.web/request
       (store
        {:type :ivr.store/get-sound-by-name
         :name soundname
         :account-id account-id
         :script-id script-id
         :on-success [::play-sound options]})})))

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
