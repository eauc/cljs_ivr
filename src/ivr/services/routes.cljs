(ns ivr.services.routes
  (:require [re-frame.core :as re-frame]
            [ivr.libs.logger :as logger]))

(defn- insert-route-in-event [[event & rest] route]
  (if event
    (vec (conj rest route event))
    []))

(defn dispatch [event]
  (fn [req res next]
    (let [route {:req req :res res :next next}]
      (re-frame/dispatch (insert-route-in-event event route)))))

(defn- merge-in-effect [context effect value]
  (if (contains? (:effects context) effect)
    (update-in context [:effects effect] merge value)
    context))

(defn- update-effect [context effect update-fn]
  (if (contains? (:effects context) effect)
    (update-in context [:effects effect] update-fn)
    context))

(defn- update-web-request-fx [request-desc route]
  (let [update-event (fn [desc event-name]
                       (if (contains? desc event-name)
                         (update desc event-name insert-route-in-event route)
                         desc))]
    (-> request-desc
        (update-event :on-success)
        (update-event :on-error))))

(defn before-route [context]
  (let [[event route & rest] (get-in context [:coeffects :event])]
    (-> context
        (assoc-in [:coeffects :event] (vec (conj rest event)))
        (assoc-in [:coeffects :route] route))))

(defn after-route [context]
  (let [{:keys [req res next] :as route} (get-in context [:coeffects :route])
        config (get-in context [:coeffects :db :config-info :config])]
    (-> context
        (update-effect :ivr.web/request #(update-web-request-fx % route))
        (update-effect :dispatch #(insert-route-in-event % route))
        (merge-in-effect :ivr.routes/response {:res res})
        (merge-in-effect :ivr.routes/next {:next next})
        (merge-in-effect :ivr.web/request {:config config}))))

(def interceptor
  (re-frame/->interceptor
   :id ::route
   :before before-route
   :after after-route))

(defn error-response [{:keys [status]
                       :or {status 500}
                       :as data}]
  {:status status
   :data data})

(re-frame/reg-fx
 :ivr.routes/response
 (fn route-response [{:keys [status content-type data res]
                      :or {status 200
                           content-type "application/json"}}]
   (-> res
       (.status status)
       (.set "Content-Type" content-type)
       (.send (clj->js data)))))

(re-frame/reg-fx
 :ivr.routes/next
 (fn route-response [{:keys [next]}]
   (next)))
