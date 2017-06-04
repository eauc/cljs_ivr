(ns ivr.services.routes.interceptor
  (:require [cljs.spec :as spec]
            [ivr.libs.logger :as logger]
            [ivr.services.routes.dispatch :as dispatch]
            [re-frame.core :as re-frame]))

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


(defn before-route
  [context]
  (let [event (get-in context [:coeffects :event])
        route (peek event)
        event-base (subvec event 1 (dec (count event)))]
    (->> (dispatch/get-route-params route)
         (assoc route :params)
         (conj event-base)
         (assoc-in context [:coeffects :event]))))


(defn- after-route [context effects]
  (let [route (get-context-route context)
        route? (spec/valid? :ivr.route/route route)]
    (if route?
      (reduce handle-effect context effects)
      context)))


(def interceptor
  (re-frame/->interceptor
    :id :ivr.routes/interceptor
    :before before-route
    :after #(after-route % @effects)))


(defn reg-fx
  [name handler]
  (if (contains? @effects name)
    (log "info" (str "overwriting " name " effect handler"))
    (log "info" (str "registering " name " effect handler")))
  (swap! effects assoc name handler))
