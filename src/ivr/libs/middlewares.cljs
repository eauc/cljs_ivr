(ns ivr.libs.middlewares
  (:require [cljs.nodejs :as nodejs]
            [ivr.debug :refer [debug? test? default-log-level]]))

(defonce body-parser (nodejs/require "body-parser"))
(defonce compression (nodejs/require "compression"))
(defonce express-winston (nodejs/require "express-winston"))
(defonce helmet (nodejs/require "helmet"))
(defonce util (nodejs/require "util"))
(defonce winston (nodejs/require "winston"))
(defonce Console (aget winston "transports" "Console"))
(defonce winston-config (nodejs/require "winston/lib/winston/config"))

(defonce request-whitelist
  (doto (aget express-winston "requestWhitelist")
    (.push "body")
    (.push "hostname")
    (.push "ip")))

(defonce response-whitelist
  (doto (aget express-winston "responseWhitelist")
    (.push "body")))

(def logger-format
  "HTTP {{req.method}} {{req.url}} {{res.statusCode}} {{res.responseTime}}ms")

(defn- log-time []
	(.toISOString (js/Date.)))

(defn- http-formatter [options]
	(let [level (aget options "level")
        level-colorized (.colorize winston-config level level)
        message (aget options "message")
        request (aget options "meta" "req")
        response (aget options "meta" "res")
        from (or (aget request "headers" "scc-from") "external")
        id (or (aget request "headers" "scc-request-id") "N/A")
        meta #js {:request request :response response}]
    (str request.ip " - " request.hostname " [" (log-time) "] "
         (if debug? level-colorized level) " "
         "[" from "->IVR] [" id "] " message " "
         (if debug?
           (str "\n" (.inspect util meta #js {:depth nil
                                              :colors true}))
           (.stringify js/JSON meta)))))

(def logger-transport
  (Console. #js {:colorize debug?
                 :formatter http-formatter
                 :label "IVR"
                 :prettyPrint debug?
                 :timestamp true}))

(defn init [app]
  (doto app
    (.set "json spaces" 4)
    (.use (.json body-parser))
    (.use (.urlencoded body-parser #js {:extended false}))
    (.use (helmet))
    (.use (compression))
    (.use (.logger express-winston
                   (clj->js {:level default-log-level
                             :meta true
                             :msg logger-format
                             :statusLevels true
                             :transports [logger-transport]})))))
