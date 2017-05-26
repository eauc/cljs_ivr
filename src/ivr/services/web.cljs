(ns ivr.services.web
  (:require [cljs.core.async :as async :refer [>!]]
            [cljs.nodejs :as nodejs]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [ivr.libs.logger :as logger]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce superagent (nodejs/require "superagent"))

(def log
  (logger/create "web"))

(def json-reader
  (transit/reader :json))

(defn- json->clj [json-string]
  (->> (or json-string "{}")
       ;; (logger/default "error" "json-string")
       (transit/read json-reader)
       walk/keywordize-keys))

(defn- parse-response-body [response callback]
  (let [text (atom "")]
    (doto response
      (.setEncoding "utf8")
      (.on "data" #(swap! text str %))
      (.on "end"
           #(try
              (let [body (json->clj @text)]
                (aset response "body" body)
                (aset response "text" @text)
                (callback))
              (catch js/Object error
                (callback error)))))))

(defn request [{:as description
                :keys [method url accept type]
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

(defn- response->event [[success? result]
                        [event-success payload-success]
                        [event-error payload-error]]
  (if (= :ivr.web/success success?)
    [event-success (assoc payload-success :response result)]
    [event-error (assoc payload-error :error result)]))

(defn- web-request-fx-run [{:as description
                            :keys [url on-success on-error]}
                           dispatch_url
                           {:keys [config insert-route-in-event]}]
  (go
    (let [absolute-url (str dispatch_url url)
          response (-> description
                       (assoc :url absolute-url)
                       request
                       <!)]
      (->
       response
       (response->event on-success on-error)
       (insert-route-in-event)
       re-frame/dispatch))))

(defn request-fx [value {:keys [config] :as options}]
  (let [descriptions (if (vector? value) value [value])
        dispatch_url (get-in config [:environment :dispatch_url :internal])]
    (mapv #(web-request-fx-run % dispatch_url options) descriptions)))
