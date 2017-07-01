(ns ivr.services.routes.effects
  (:require [ivr.libs.json :refer [clj->json]]
            [ivr.libs.logger :as logger]
            [ivr.libs.xml :refer [clj->xml]]
            [ivr.services.routes.dispatch :as dispatch]
            [ivr.services.routes.interceptor :as interceptor]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def log
  (logger/create "routes"))


(defn- params-fx [context route new-params]
  (->> new-params
       (log "debug" "Update ivr-params")
       (dispatch/set-route-params route)))

(interceptor/reg-fx
  :ivr.routes/params
  params-fx)


(defn- response-fx [context {:keys [res] :as route}
                    {:keys [status type data]
                     :or {status 200 type "json"}}]
  (when (aget res "ivr-sent")
    (throw (js/Error. "routes-response: response already sent!")))
  (-> res
      (.status status)
      (.type type)
      (.send (if (= type "json")
               (clj->json data)
               (clj->xml data)))
      (aset "ivr-sent" true)))

(interceptor/reg-fx
  :ivr.routes/response
  response-fx)


(defn- next-fx [context {:keys [next] :as route} _]
  (go (next)))

(interceptor/reg-fx
  :ivr.routes/next
  next-fx)


(defn- dispatch-fx [context route event]
  (->> route
       (dispatch/insert-route-in-event event)
       (re-frame/dispatch)))

(interceptor/reg-fx
  :ivr.routes/dispatch
  dispatch-fx)
