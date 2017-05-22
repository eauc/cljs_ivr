(ns ivr.core
  (:require [cljs.nodejs :as nodejs]
            [ivr.db]
            [ivr.libs.logger :as logger]
            [ivr.libs.middlewares :as middlewares]
            [ivr.libs.nock]
            [ivr.routes.action :as action-routes]
            [ivr.routes.api-v1 :as api-v1-routes]
            [ivr.routes.config :as config-routes]
            [ivr.routes.index :as index-routes]
            [ivr.services.config :as config]
            [re-frame.core :as re-frame]))

(nodejs/enable-util-print!)

(defonce express (nodejs/require "express"))
(defonce http (nodejs/require "http"))

(defonce app (atom))

(defn create-app []
  (logger/default "info" "Create express app")
  (reset! app
          (-> (express)
              (middlewares/init)
              (index-routes/init)
              (api-v1-routes/init)
              (config-routes/init)
              (action-routes/init))))

(defn- start-server [config]
  (create-app)
  (-> http
      (.createServer #(@app %1 %2))
      (.listen (:port config)
               #(logger/default "info" "Server started on port"
                                (:port config)))))

(defn -main []
  (let [env {:port (or (some-> js/process
                               (aget "env" "PORT")
                               (js/parseInt)) 3000)}
        config-paths (-> js/process
                         (aget "argv")
                         (.slice 2)
                         (js->clj))
        config-layers (into (mapv (fn [path] {:path path}) config-paths)
                            [{:desc "env" :config env}])]
    (config/init {:layers config-layers
                  :http-retry-timeout-s 10
                  :http-retry-delay-s 2
                  :on-success (fn [config-info]
                                (logger/default "info" "Config loaded" (:config config-info))
                                (re-frame/dispatch-sync [:ivr.db/init config-info])
                                (start-server (:config config-info)))
                  :on-error (fn []
                              (logger/default "error" "Config load failed")
                              (.exit js/process 1))})))

(set! *main-cli-fn* -main)
