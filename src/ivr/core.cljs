(ns ivr.core
  (:require [cljs.nodejs :as nodejs]
            [re-frame.core :as re-frame]))

(nodejs/enable-util-print!)

(defonce express (nodejs/require "express"))
(defonce http (nodejs/require "http"))

(defonce app (atom))

(defn indexRoute [_ [_ {:keys [req res] :as route}]]
  (.log js/console "route" route)
  (-> res (.send #js {:hello "World"}))
  {})

(defn create-app []
  (.log js/console "Create express app")
  (reset! app
          (doto (express)
            (.get "/" (fn [req res]
                        (re-frame/dispatch [:index-route {:req req :res res}]))))))

(defn -main []
  (let [port (or (some-> js/process
                         (aget "env" "PORT")
                         (js/parseInt)) 3000)]
    (create-app)
    (-> http
        (.createServer #(@app %1 %2))
        (.listen port #(.log js/console "Server started on port" port)))))

(set! *main-cli-fn* -main)

(re-frame/reg-event-fx
 :index-route
 indexRoute)
