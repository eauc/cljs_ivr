(ns ivr.routes.action
  (:require [cljs.nodejs :as nodejs]
            [ivr.db :as db]
            [ivr.models.script :as script]
            [ivr.models.store :as store]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [ivr.services.calls]
            [re-frame.core :as re-frame]))

(defonce express (nodejs/require "express"))

(def base-url
  (str (get-in url/config [:apis :v1 :link])
       (get-in url/config [:apis :v1 :action :link])))

(def script-start-url
  (get-in url/config [:apis :v1 :action :script-start]))

;; (def script-enter-node-url
;;   (get-in url/config [:apis :v1 :action :script-enter-node]))

;; (def script-leave-node-url
;;   (get-in url/config [:apis :v1 :action :script-leave-node]))

(def resolve-or-create-call-middleware
  (routes/dispatch [:ivr.services.calls/resolve {:create? true}]))

(def resolve-script-middleware
  (routes/dispatch [:ivr.models.script/resolve]))

(def script-start-route
  (routes/dispatch [:ivr.models.script/start-route]))

;; (def script-enter-node-route
;;   (routes/dispatch [:ivr.models.script/enter-node-route]))

;; (def script-leave-node-route
;;   (routes/dispatch [:ivr.models.script/leave-node-route]))

(def router
  (doto (.Router express #js {:mergeParams true})
    (.use resolve-script-middleware)
    (.use script-start-url resolve-or-create-call-middleware)
    (.get script-start-url script-start-route)
    ;; (.get script-enter-node-url script-enter-node-route)
    ;; (.get script-leave-node-url script-leave-node-route)
    ))

(defn init [app]
  (doto app
    (.use base-url router)))
