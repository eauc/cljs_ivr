(ns ivr.libs.logger
  (:require [cljs.nodejs :as nodejs]
            [ivr.debug :refer [debug? test?]]
            [clojure.string :as str]))

(defonce winston (nodejs/require "winston"))
(defonce Console (aget winston "transports" "Console"))
(defonce Logger (aget winston "Logger"))

(def default-log-level
  (cond
    test? "warn"
    debug? "debug"
    :default "info"))

(def log-level
  (or (aget js/process "env" "LOG_LEVEL")
      default-log-level))

(defn- transport [label]
  (Console. #js {:colorize debug?
                 :label label
                 :level log-level
                 :prettyPrint debug?}))

(defn create [label]
  (let [fullLabel (str/join "." (remove nil? ["IVR" label]))
        logger (Logger. (clj->js {:transports [(transport fullLabel)]}))]
    (fn log [level message data]
      (.log logger level message (clj->js data))
      data)))

(def default (create nil))
