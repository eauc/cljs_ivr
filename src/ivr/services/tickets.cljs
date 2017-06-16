(ns ivr.services.tickets
  (:require [cljs.nodejs :as nodejs]
            [ivr.libs.json :refer [clj->json]]
            [ivr.libs.logger :as logger]
            [re-frame.core :as re-frame]))

(defonce zeromq (nodejs/require "zeromq"))


(def log
  (logger/create "tickets"))


(defonce send-msg
  (atom #(log "error" "No publication channel initialized" %)))


(defonce ticket-order
  (atom 0))


(defn socket-send-msg
  [{:keys [socket producer ticket-order version]}
   raw-ticket]
  (let [ticket (-> raw-ticket
                   (assoc :version version)
                   (assoc :order @ticket-order)
                   (cond-> (not (nil? producer)) (update :producer #(or % producer))))]
    (swap! ticket-order inc)
    (-> socket (.send (clj->json (log "info" "send ticket" ticket))))))


(defn init
  [config]
  (let [producer (get-in config [:zmq :publisherName])
        publish-to (get-in config [:zmq :publishTo])
        version (get-in config [:zmq :ticket_version])
        socket (-> zeromq
                   (.socket "pub")
                   (.bindSync publish-to))]
    (log "info" "Publish channel bound"
         {:producer producer
          :publish-to publish-to
          :version version})
    (reset! send-msg (partial socket-send-msg {:socket socket
                                               :producer producer
                                               :ticket-order ticket-order
                                               :version version}))))

(re-frame/reg-fx
  :ivr.ticket/emit
  (fn ticket-emit-fx
    [ticket]
    (@send-msg ticket)))
