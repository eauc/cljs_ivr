(ns ivr.routes.config
  (:require [cljs.nodejs :as nodejs]
            [ivr.db :as db]
            [ivr.routes.url :as url]
            [ivr.services.config :as config]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))

(defonce express (nodejs/require "express"))

(def base-url
  (str (get-in url/config [:apis :v1 :link])
       (get-in url/config [:apis :v1 :config :link])))

(def explain-url
  (get-in url/config [:apis :v1 :config :explain]))

(def explain-route
  (routes/dispatch [::explain-route]))

(def router
  (doto (.Router express)
    (.get explain-url explain-route)))

(defn init [app]
  (doto app
    (.use base-url router)))

(re-frame/reg-event-fx
 ::explain-route
 [routes/interceptor
  db/default-interceptors]
 (fn config-explain-route [{:keys [db route]} _]
   (let [{:keys [req]} route
         query (js->clj (aget req "query") :keywordize-keys true)
         explanation (config/explain (:config-info db) query)
         link (url/absolute [:v1 :config :explain])]
     {:ivr.routes/response
      {:data (merge explanation
                    {:link link})}})))
