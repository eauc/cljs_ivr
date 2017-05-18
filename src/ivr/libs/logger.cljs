(ns ivr.libs.logger
  (:require [cljs.nodejs :as nodejs]
            [ivr.debug :refer [debug?]]
            [clojure.string :as str]))

(defonce winston (nodejs/require "winston"))
(defonce Console (aget winston "transports" "Console"))
(defonce Logger (aget winston "Logger"))

(defn- transport [label]
  (Console. #js {:colorize debug?
                 :label label
                 :prettyPrint debug?}))

(defn create [label]
  (let [fullLabel (str/join "." (remove nil? ["IVR" label]))
        logger (Logger. (clj->js {:transports [(transport fullLabel)]}))]
    (fn log [level message data]
      (.log logger level message (clj->js data)))))

(def default (create nil))
