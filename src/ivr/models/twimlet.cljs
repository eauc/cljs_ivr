(ns ivr.models.twimlet
  (:require [ivr.services.routes :as routes]
            [ivr.models.verbs :as verbs]
            [re-frame.core :as re-frame]))


(defn loop-play-route
  [{:keys [verbs]}
   {{:strs [file]} :params :as route}]
  {:ivr.routes/response
   (verbs [{:type :ivr.verbs/loop-play
            :path (str "/cloudstore/file/" file)}])})

(routes/reg-action
  :ivr.twimlet/loop-play-route
  [(re-frame/inject-cofx :ivr.verbs/cofx)]
  loop-play-route)


(defn welcome-route
  [{:keys [db verbs]}]
  (let [welcome-sound (get-in db [:config-info :config :business :welcome-sound])]
    {:ivr.routes/response
     (verbs [{:type :ivr.verbs/loop-play
              :path (str "/cloudstore/file/" welcome-sound)}])}))

(routes/reg-action
  :ivr.twimlet/welcome-route
  [(re-frame/inject-cofx :ivr.verbs/cofx)]
  welcome-route)
