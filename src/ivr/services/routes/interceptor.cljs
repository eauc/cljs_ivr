(ns ivr.services.routes.interceptor
  (:require [re-frame.core :as re-frame]
            [ivr.libs.logger :as logger]
            [cljs.spec :as spec]))


(def log
  (logger/create "routes"))


(defonce effects
  (atom {}))


(defn- get-context-route [context]
  (peek (get-in context [:coeffects :event])))


(defn- handle-effect [{:keys [effects] :as context}
                      [effect-name handle-fn]]
  (if (contains? effects effect-name)
    (let [route (get-context-route context)
          effect-value (get effects effect-name)]
      (log "debug" "handle-effect" {:name effect-name
                                    :value effect-value})
      (handle-fn context route effect-value)
      (update context :effects dissoc effect-name))
    context))


(defn- after-route [context effects]
  (let [route (get-context-route context)
        route? (spec/valid? :ivr.route/route route)]
    (if route?
      (reduce handle-effect context effects)
      context)))


(def interceptor
  (re-frame/->interceptor
   :id :ivr.routes/interceptor
   :after #(after-route % @effects)))


(defn reg-fx
  [name handler]
  (if (contains? @effects name)
    (log "info" (str "overwriting " name " effect handler"))
    (log "info" (str "registering " name " effect handler")))
  (swap! effects assoc name handler))
