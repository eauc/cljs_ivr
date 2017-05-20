(ns ivr.routes.config
  (:require [cljs.nodejs :as nodejs]
            [ivr.services.config]
            [re-frame.core :as re-frame]))

(defonce express (nodejs/require "express"))

(defn- configRoute [req res]
  (re-frame/dispatch [:ivr.services.config/explain-route req res]))

(def router
  (doto (.Router express)
    (.get "/" configRoute)))

