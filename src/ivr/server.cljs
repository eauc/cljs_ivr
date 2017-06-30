(ns ivr.server
  (:require [cljs.nodejs :as nodejs]
            [ivr.db]
            [ivr.libs.logger :as logger]
            [ivr.libs.middlewares :as middlewares]
            [ivr.routes.action :as action-routes]
            [ivr.routes.api-v1 :as api-v1-routes]
            [ivr.routes.call :as call-routes]
            [ivr.routes.config :as config-routes]
            [ivr.routes.index :as index-routes]
            [ivr.services.config :as config]
            [ivr.services.tickets :as tickets]
            [re-frame.core :as re-frame]))

(defonce express (nodejs/require "express"))
(defonce http (nodejs/require "http"))

(defonce app (atom))
(defonce server (atom))


(defn create-app []
  (logger/default "info" "Create express app")
  (reset! app
          (-> (express)
              (middlewares/init)
              (index-routes/init)
              (api-v1-routes/init)
              (call-routes/init)
              (config-routes/init)
              (action-routes/init))))


(defn http-server
  [{:keys [resolve reject]} config]
  (create-app)
  (reset! server (-> http
                     (.createServer #(@app %1 %2))
                     (.listen (:port config)
                              (fn [error]
                                (if error
                                  (reject error)
                                  (resolve (logger/default "info" "Server started on port"
                                                           (:port config)))))))))


(defn app-init
  [promise config-info]
  (logger/default "info" "Config loaded" (:config config-info))
  (re-frame/dispatch-sync [:ivr.db/init {:config-info config-info}])
  (tickets/init (:config config-info))
  (http-server promise (:config config-info)))


(defn start
  [config-layers]
  (js/Promise.
    (fn [resolve reject]
      (config/init {:layers config-layers
                    :http-retry-timeout-s 10
                    :http-retry-delay-s 2
                    :on-success (partial app-init {:resolve resolve
                                                   :reject reject})
                    :on-error (fn []
                                (logger/default "error" "Config load failed")
                                (reject))}))))


(defn start-test
  [config]
  (start [{:desc "test config" :config (js->clj config :keywordize-keys true)}]))


(defn stop
  []
  (js/Promise.
    (fn [resolve]
      (tickets/stop)
      (.close @server resolve))))
