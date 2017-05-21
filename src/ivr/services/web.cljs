(ns ivr.services.web
  (:require [cljs.core.async :as async :refer [>!]]
            [cljs.nodejs :as nodejs]
            [ivr.libs.logger :as logger]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce superagent (nodejs/require "superagent"))

(def log
  (logger/create "web"))

(defn request [{:as description
                :keys [method url accept]
                :or {method "GET"
                     accept "json"}}]
  (let [result (async/chan 1)]
    (log "debug" "start" description)
    (-> (superagent method url)
        (.accept accept)
        (.then
         (fn [response]
           (log "info" "request success"
                {:method method
                 :url url
                 :status (aget response "status")})
           (go (>! result [:ivr.web/success response])))
         (fn [error]
           (go (>! result [:ivr.web/error (log "warn" "request failed"
                                               {:method method
                                                :url url
                                                :message (aget error "message")})])))))
    result))

(defn web-request-fx-run [{:as description
                           :keys [config url on-success on-error]}]
  (go
    (let [dispatch-url (get-in config [:environment :dispatch_url :internal])
          absolute-url (str dispatch-url url)
          [result payload] (<! (request (assoc description :url absolute-url)))]
      (re-frame/dispatch
       (conj (if (= result :ivr.web/success)
               on-success
               on-error) payload)))))

(re-frame/reg-fx
 :ivr.web/request
 (fn web-request-fx [value]
   (let [descriptions [value]]
     ;; (log "debug" "web-request-fx" descriptions)
     (mapv web-request-fx-run descriptions))))
