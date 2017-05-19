(ns ivr.services.config
  (:require [cljs.nodejs :as nodejs]
            [cljs.spec :as spec]
            [ivr.libs.logger :as logger]
            [ivr.services.config.base :as base :refer [log]]
            [ivr.services.config.file]
            [ivr.services.config.http]
            [ivr.services.config.object]
            [ivr.services.config.invalid]))

(spec/fdef init
           :args (spec/keys :req-un [:ivr.config/layers
                                     :ivr.config.layer.load/http-retry-timeout-s
                                     :ivr.config.layer.load/http-retry-delay-s])
           :ret :ivr.config/info)
(defn init [{:as options
             :keys [layers on-success on-error]}]
  (log "debug" "init" options)
  (let [load (->> layers
                  (mapv #(base/load-layer % options))
                  (clj->js)
                  (.all js/Promise))]
    (.then load
           (fn [load-results]
             (let [results (js->clj load-results)
                   config (apply merge (mapv :config load-results))
                   errors (remove nil? (mapv :error load-results))
                   info {:config config :loads results}]
               (if (empty? errors)
                 (on-success info)
                 (on-error info)))))))
