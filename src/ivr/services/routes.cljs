(ns ivr.services.routes
  (:require [cljs.spec :as spec]
            [ivr.services.web :as web]
            [ivr.specs.route]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn error-response [{:keys [status]
                       :or {status 500}
                       :as data}]
  {:status status
   :data data})

(defn- insert-route-in-event [event route]
  (conj (vec event) route))

(defn dispatch [event]
  (fn [req res next]
    (let [route {:req req :res res :next next}]
      (re-frame/dispatch (insert-route-in-event event route)))))

(defn- get-route [context]
  (last (get-in context [:coeffects :event])))

(defn- update-route-in-event [event route]
  (-> event
      count
      dec
      (take event)
      vec
      (conj route)))

(defn- update-route [context route]
  (update-in context [:coeffects :event] update-route-in-event route))

(defn- routes-response [context {:keys [res] :as route}
                        {:keys [status content-type data]
                         :or {status 200 content-type "application/json"}}]
  (when (nil? res) (throw (js/Error. "routes-response: response already sent!")))
  (-> res
      (.status status)
      (.set "Content-Type" content-type)
      (.send (clj->js data)))
  (update-route context (dissoc route :res)))

(defn- routes-next [context {:keys [next] :as route} _]
  (when (nil? next) (throw (js/Error. "routes-next: next already called!")))
  (go (next))
  (update-route context (dissoc route :next)))

(defn- web-request [context {:keys [req] :as route} request]
  (let [config (get-in context [:coeffects :db :config-info :config])]
    (web/request-fx request {:config config :req req
                             :insert-route-in-event #(insert-route-in-event % route)})
    context))

(defn- handle-effect [context effect-name handle-fn]
  (if (contains? (:effects context) effect-name)
    (let [route (get-route context)]
      (-> context
          (handle-fn route (get-in context [:effects effect-name]))
          (update :effects dissoc effect-name)))
    context))

(defn- handle-next-dispatch [context]
  (if (contains? (:effects context) :dispatch)
    (let [route (get-route context)]
      (update-in context [:effects :dispatch] conj route))
    context))

(defn- after-route [context effect-handlers]
  (let [route (get-route context)
        route? (spec/valid? :ivr.route/route route)]
    (if-not route?
      context
      (->> effect-handlers
           (reduce #(handle-effect %1 (:key %2) (:handler %2)) context)
           handle-next-dispatch))))

(def routes-effects
  [{:key :ivr.routes/response
    :handler routes-response}
   {:key :ivr.routes/next
    :handler routes-next}
   {:key :ivr.web/request
    :handler web-request}])

(def interceptor
  (re-frame/->interceptor
   :id :ivr.routes/interceptor
   :after #(after-route % routes-effects)))
