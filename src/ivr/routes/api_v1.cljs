(ns ivr.routes.api-v1
  (:require [cljs.nodejs :as nodejs]
            [ivr.routes.url :as url]
            [ivr.routes.action :as action-routes]
            [ivr.routes.config :as config-routes]
            ))

(defonce express (nodejs/require "express"))

(def base-url
  (get-in url/config [:apis :v1]))

(defn- index-route [req res]
  (-> res (.send (clj->js {:link base-url
                           :version "V1"
                           :action (action-routes/describe)
                           :config (config-routes/describe)}))))

(def router
  (doto (.Router express)
    (.get "/" index-route)))

(defn init [app]
  (doto app
    (.use base-url router)))
