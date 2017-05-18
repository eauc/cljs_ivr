(ns ivr.core
  (:require [cljs.nodejs :as nodejs]
            [ivr.libs.middlewares :as middlewares]
            [ivr.routes.index :as index]
            [ivr.libs.logger :as logger]))

(nodejs/enable-util-print!)

(defonce express (nodejs/require "express"))
(defonce http (nodejs/require "http"))

(defonce app (atom))

(defn create-app []
  (logger/default "info" "Create express app")
  (reset! app
          (-> (express)
              (middlewares/init)
              (.use "/" index/router))))

(defn -main []
  (let [port (or (some-> js/process
                         (aget "env" "PORT")
                         (js/parseInt)) 3000)]
    (create-app)
    (-> http
        (.createServer #(@app %1 %2))
        (.listen port #(logger/default "info" "Server started on port" port)))))

(set! *main-cli-fn* -main)
