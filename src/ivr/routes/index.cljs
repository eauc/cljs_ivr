(ns ivr.routes.index
  (:require [cljs.nodejs :as nodejs]))

(defonce express (nodejs/require "express"))

(defn- indexRoute [req res]
  (-> res (.send (clj->js {:module "IVR"
                           :version "1.0.0"
                           :apis {:v1 "/v1"}}))))

(def router
  (doto (.Router express)
    (.get "/" indexRoute)))
