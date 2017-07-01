(ns ivr.services.routes.dispatch
  (:require [ivr.libs.json :refer [json->clj]]
            [ivr.libs.logger :as logger]
            [re-frame.core :as re-frame]))


(def log
  (logger/create "routes"))


(defn- get-route-params [{:keys [req]}]
  (aget req "ivr-params"))


(defn set-route-params [{:keys [req]} params]
  (aset req "ivr-params" params))


(defn- refresh-route-params [{:keys [req] :as route}]
  (let [express-params (js->clj (aget req "params"))
        params (-> (get-route-params route)
                   (or (let [body (js->clj (aget req "body"))
                             query (js->clj (aget req "query"))]
                         (merge body query)))
                   (merge express-params))]
    (->> params
         (log "debug" "Extract ivr-params")
         (set-route-params route))))


(defn insert-route-in-event [event route]
  (conj (vec event) route))


(defn dispatch [event]
  (fn [req res next]
    (let [route {:req req :res res :next next}]
      (refresh-route-params route)
      (re-frame/dispatch (insert-route-in-event event route)))))
