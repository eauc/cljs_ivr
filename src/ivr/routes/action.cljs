(ns ivr.routes.action
  (:require [cljs.nodejs :as nodejs]
            [ivr.db :as db]
            [ivr.models.script :as script]
            [ivr.models.store :as store]
            [ivr.routes.url :as url]
            [ivr.services.routes.dispatch :as routes-dispatch]
            [ivr.services.calls]
            [re-frame.core :as re-frame]))

(defonce express (nodejs/require "express"))


(def base-url
  (str (get-in url/config [:apis :v1 :link])
       (get-in url/config [:apis :v1 :action :link])))

(def script-start-url
  (get-in url/config [:apis :v1 :action :script-start]))

(def script-enter-node-url
  (get-in url/config [:apis :v1 :action :script-enter-node]))

(def script-leave-node-url
  (get-in url/config [:apis :v1 :action :script-leave-node]))


(def resolve-or-create-call-middleware
  (routes-dispatch/dispatch [:ivr.call/resolve {:create? true}]))

(def resolve-call-middleware
  (routes-dispatch/dispatch [:ivr.call/resolve {:create? false}]))

(def resolve-script-middleware
  (routes-dispatch/dispatch [:ivr.script/resolve-script]))

(def resolve-enter-node-middleware
  (routes-dispatch/dispatch [:ivr.script/resolve-node {:action :enter}]))

(def resolve-leave-node-middleware
  (routes-dispatch/dispatch [:ivr.script/resolve-node {:action :leave}]))

(def resolve-start-node-middleware
  (routes-dispatch/dispatch [:ivr.script/resolve-start-node]))


(def script-enter-node-route
  (routes-dispatch/dispatch [:ivr.script/enter-node-route]))

(def script-leave-node-route
  (routes-dispatch/dispatch [:ivr.script/leave-node-route]))


(def router
  (doto (.Router express #js {:mergeParams true})

    (.use resolve-script-middleware)

    (.use script-start-url resolve-or-create-call-middleware)
    (.use script-start-url resolve-start-node-middleware)
    (.get script-start-url script-enter-node-route)

    (.use script-enter-node-url resolve-call-middleware)
    (.use script-enter-node-url resolve-enter-node-middleware)
    (.get script-enter-node-url script-enter-node-route)

    (.use script-leave-node-url resolve-call-middleware)
    (.use script-leave-node-url resolve-leave-node-middleware)
    (.get script-leave-node-url script-leave-node-route)))


(defn init [app]
  (doto app
    (.use base-url router)))
