(ns ivr.libs.middlewares
  (:require [cljs.nodejs :as nodejs]
            [ivr.debug :refer [debug?]]))

(defonce body-parser (nodejs/require "body-parser"))
(defonce compression (nodejs/require "compression"))
(defonce express-winston (nodejs/require "express-winston"))
(defonce helmet (nodejs/require "helmet"))
(defonce winston (nodejs/require "winston"))
(defonce Console (aget winston "transports" "Console"))

(def logger-transport
  (Console. #js {:colorize debug?
                 :label "IVR"
                 :prettyPrint debug?
                 :timestamp true}))

(def logger-format
  "HTTP {{req.method}} {{req.url}} {{res.statusCode}} {{res.responseTime}}ms")

(defn init [app]
  (doto app
    (.set "json spaces" 4)
    (.use (helmet))
    (.use (compression))
    (.use (.json body-parser))
    (.use (.logger express-winston
                   (clj->js {:transports [logger-transport]
                             :meta true
                             :msg logger-format})))))
