(ns ivr.services.web
  (:require [cljs.core.async :as async :refer [>!]]
            [cljs.nodejs :as nodejs]
            [ivr.libs.json :refer [clj->json json->clj]]
            [ivr.libs.logger :as logger]
            [ivr.services.routes.dispatch :as routes-dispatch]
            [ivr.services.routes.interceptor :as routes-interceptor]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defonce superagent (nodejs/require "superagent"))


(def log
  (logger/create "web"))


(defn- parse-response-body [response callback]
  (let [text (atom "")]
    (doto response
      (.setEncoding "utf8")
      (.on "data" #(swap! text str %))
      (.on "end"
           #(if-not (empty? @text)
              (try
                (let [body (json->clj @text)]
                  (aset response "body" body)
                  (aset response "text" @text)
                  (callback))
                (catch js/Object error
                  (callback error)))
              (callback))))))


(defn request [{:as description
                :keys [method url accept data type]
                :or {method "GET"
                     accept "json"
                     type "json"}}]
  (let [result (async/chan 1)]
    (log "debug" "start" description)
    (-> (superagent method url)
        (.type type)
        (.accept accept)
        (.buffer true)
        (.parse parse-response-body)
        (.send (clj->json data))
        (.then
          (fn [response]
            (log "info" "request success"
                 {:method method
                  :url url
                  :status (aget response "status")})
            (go (>! result [:ivr.web/success response])))
          (fn [error]
            (go (>! result [:ivr.web/error
                            (log "warn" "request failed"
                                 {:method method
                                  :url url
                                  :message (aget error "message")})])))))
    result))


(defn- default-handler
  [as result]
  (log "warn" (str "Undefined " as " result handler") {:result result}))


(defn- handler->event [handler as result]
  (if (vector? handler)
    (let [[event-name event-payload] handler]
      [event-name (assoc event-payload as result)])
    (if (nil? handler)
      (default-handler as result)
      (handler result))))


(defn- response->event [[success? result]
                        on-success on-error]
  (if (= :ivr.web/success success?)
    (handler->event on-success :response result)
    (handler->event on-error :error result)))


(defn- request-fx [context route
                   {:keys [url on-success on-error] :as description}]
  (let [config (get-in context [:coeffects :db :config-info :config])
        dispatch-url (get-in config [:environment :dispatch_url :internal])
        absolute-url (str dispatch-url url)]
    (go
      (let [response (-> description
                         (assoc :url absolute-url)
                         request
                         <!)]
        (-> response
            (response->event on-success on-error)
            (routes-dispatch/insert-route-in-event route)
            re-frame/dispatch)))))

(routes-interceptor/reg-fx
  :ivr.web/request
  request-fx)
