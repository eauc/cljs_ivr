(ns ivr.services.tickets
  (:require [cljs.nodejs :as nodejs]
            [ivr.libs.json :refer [clj->json]]
            [ivr.libs.logger :as logger]
            [re-frame.core :as re-frame]))

(defonce zeromq (nodejs/require "zeromq"))


(def log
  (logger/create "tickets"))


(defonce socket-state
  (atom))


(defonce ticket-order
  (atom 0))


(defn send-string
  [socket string]
  (-> socket (.send string)))


(defn send-msg
  [{:keys [socket producer version]}
   ticket-order raw-ticket]
  (let [ticket (-> raw-ticket
                   (assoc :version version)
                   (assoc :order @ticket-order)
                   (cond-> (not (nil? producer)) (update :producer #(or % producer))))]
    (swap! ticket-order inc)
    (send-string socket (clj->json (log "info" "send ticket" ticket)))))


(defn init
  [config]
  (let [producer (get-in config [:zmq :publisherName])
        publish-to (get-in config [:zmq :publishTo])
        version (get-in config [:zmq :ticket_version])
        new-socket (-> zeromq
                       (.socket "pub")
                       (.bindSync publish-to))]
    (log "info" "Publish channel bound"
         {:producer producer
          :publish-to publish-to
          :version version})
    (reset! socket-state {:socket new-socket
                          :producer producer
                          :version version})))


(defn stop
  []
  (-> @socket-state :socket .close))


(re-frame/reg-fx
  :ivr.ticket/emit
  (fn ticket-emit-fx
    [ticket]
    (send-msg @socket-state ticket-order ticket)))
