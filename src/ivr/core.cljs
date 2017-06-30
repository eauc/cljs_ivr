(ns ivr.core
  (:require [cljs.nodejs :as nodejs]
            [ivr.server :as server]
            [ivr.libs.nock]))

(nodejs/enable-util-print!)


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
    (-> (server/start config-layers)
        (.catch #(.exit js/process 1)))))


(set! *main-cli-fn* -main)
