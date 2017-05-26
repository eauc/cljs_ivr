(ns ivr.services.routes
  (:require [cljs.spec :as spec]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [hiccups.runtime :as hiccupsrt]
            [ivr.libs.logger :as logger]
            [ivr.services.web :as web]
            [ivr.specs.route]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [hiccups.core :as hiccup :refer [html]]))


(def log
  (logger/create "Routes"))


(def json-reader
  (transit/reader :json))


(defn- json->clj [json-string]
  (->> (or json-string "{}")
       (transit/read json-reader)
       walk/keywordize-keys))


(def json-writer
  (transit/writer :json-verbose))


(defn- clj->json [data]
  (->> data
       walk/stringify-keys
       (transit/write json-writer)))


(defn- clj->xml [clj-data]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
       (html clj-data)))


(defn error-response [{:keys [status]
                       :or {status 500}
                       :as data}]
  {:status status
   :data data})


(defn- insert-route-in-event [event route]
  (conj (vec event) route))


(defn- get-route-params [req]
  (or (aget req "ivr-params")
      (let [body (json->clj (aget req "text"))
            params (js->clj (aget req "params") :keywordize-keys true)
            query (js->clj (aget req "query") :keywordize-keys true)]
        (log "info" "Extract ivr-params"
             (merge body query params)))))


(defn dispatch [event]
  (fn [req res next]
    (let [params (get-route-params req)
          route {:req req :res res :next next :params params}]
      (re-frame/dispatch (insert-route-in-event event route)))))


(defn- get-context-route [context]
  (last (get-in context [:coeffects :event])))


(defn- update-route-in-event [event route]
  (-> (count event)
      dec
      (take event)
      vec
      (conj route)))


(defn- update-context-route [context route]
  (update-in context [:coeffects :event] update-route-in-event route))


(defn- routes-params-fx [context route new-params]
  (log "info" "Update ivr-params" new-params)
  (update-context-route context (assoc route :params new-params)))


(defn- routes-response-fx [context {:keys [res] :as route}
                           {:keys [status type data]
                            :or {status 200 type "json"}}]
  (when (nil? res) (throw (js/Error. "routes-response: response already sent!")))
  (-> res
      (.status status)
      (.type type)
      (.send (if (= type "json")
               (clj->json data)
               (clj->xml data))))
  (update-context-route context (dissoc route :res)))


(defn- routes-next-fx [context {:keys [next] :as route} _]
  (when (nil? next) (throw (js/Error. "routes-next: next already called!")))
  (go (next))
  (update-context-route context (dissoc route :next)))


(defn- web-request-fx [context {:keys [req] :as route} request]
  (let [config (get-in context [:coeffects :db :config-info :config])]
    (web/request-fx request {:config config :req req
                             :insert-route-in-event #(insert-route-in-event % route)})
    context))


(defn- handle-effect [context effect-name handle-fn]
  (if (contains? (:effects context) effect-name)
    (let [route (get-context-route context)]
      (-> context
          (handle-fn route (get-in context [:effects effect-name]))
          (update :effects dissoc effect-name)))
    context))


(defn- handle-next-dispatch [context]
  (if (contains? (:effects context) :dispatch)
    (let [route (get-context-route context)]
      (update-in context [:effects :dispatch] conj route))
    context))


(defn- save-route-params [context]
  (let [{:keys [req params]} (get-context-route context)]
    (aset req "ivr-params" params))
  context)


(defn- after-route [context effect-handlers]
  (let [route (get-context-route context)
        route? (spec/valid? :ivr.route/route route)]
    (if-not route?
      context
      (->> effect-handlers
           (reduce #(handle-effect %1 (:key %2) (:handler %2)) context)
           handle-next-dispatch
           save-route-params))))


(def routes-effects
  [{:key :ivr.routes/params
    :handler routes-params-fx}
   {:key :ivr.routes/response
    :handler routes-response-fx}
   {:key :ivr.routes/next
    :handler routes-next-fx}
   {:key :ivr.web/request
    :handler web-request-fx}])


(def interceptor
  (re-frame/->interceptor
   :id :ivr.routes/interceptor
   :after #(after-route % routes-effects)))
