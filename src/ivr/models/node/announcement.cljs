(ns ivr.models.node.announcement
  (:require [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))

(defn- conform-preset [node]
  (let [preset (:preset node)
        value (:value preset)
        varname (:varname preset)]
    (if (and preset
             (string? value)
             (string? varname) (not (empty? varname)))
      (if (re-find #"^\$" value)
        (assoc node :preset {:type :ivr.node.announcement.preset/copy
                             :from (keyword (subs value 1))
                             :to (keyword varname)})
        (assoc node :preset {:type :ivr.node.announcement.preset/set
                             :value value
                             :to (keyword varname)}))
      (dissoc node :preset))))

(defmethod node/conform-type "announcement"
  [node]
  (-> node
      conform-preset))

(defn- apply-preset [node {:keys [call-id action-data]}]
  (case (get-in node [:preset :type])
    :ivr.node.announcement.preset/copy
    (let [{:keys [from to]} (:preset node)
          value (get action-data from)]
      {:ivr.call/action-data {:call-id call-id
                              :data (assoc action-data to value)}})
    :ivr.node.announcement.preset/set
    (let [{:keys [to value]} (:preset node)]
      {:ivr.call/action-data {:call-id call-id
                              :data (assoc action-data to value)}})
    {}))

(defn- go-to-next [{:keys [script-id next]} {:keys [verbs]}]
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
  (let [update-action-data (apply-preset node options)
        result (if disabled
                 (go-to-next node options)
                 {:ivr.web/request
                  (store
                   {:type :ivr.store/get-sound-by-name
                    :name soundname
                    :account-id account-id
                    :script-id script-id
                    :on-success [::play-sound options]})})]
    (merge update-action-data result)))

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
