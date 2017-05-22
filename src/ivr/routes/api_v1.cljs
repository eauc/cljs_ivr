(ns ivr.routes.api-v1
  (:require [cljs.nodejs :as nodejs]
            [ivr.routes.url :as url]
            [ivr.routes.config :as config-routes]))

(defonce express (nodejs/require "express"))

(def base-url
  (get-in url/config [:apis :v1 :link]))

(defn- index-route [req res]
  (-> res (.send (clj->js (url/describe [:v1])))))

(def router
  (doto (.Router express)
    (.get "/" index-route)))

(defn init [app]
  (doto app
    (.use base-url router)))
