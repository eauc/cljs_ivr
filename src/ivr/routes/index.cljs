(ns ivr.routes.index
  (:require [cljs.nodejs :as nodejs]
            [ivr.routes.url :as url]))

(defonce express (nodejs/require "express"))

(defn- index-route [req res]
  (-> res (.send (clj->js (update-in url/config [:apis :v1] select-keys [:link])))))

(def router
  (doto (.Router express)
    (.get "/" index-route)))

(defn init [app]
  (doto app
    (.use "/" router)))
