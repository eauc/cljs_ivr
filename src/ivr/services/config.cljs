(ns ivr.services.config
  (:require [cljs.nodejs :as nodejs]
            [cljs.spec :as spec]
            [ivr.libs.logger :as logger]
            [ivr.services.config.base :as base :refer [log]]
            [ivr.services.config.file]
            [ivr.services.config.http]
            [ivr.services.config.object]
            [ivr.services.config.invalid]
            [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn all-chans [chans]
  (go-loop [[c & rest] chans
            result []]
    (if (nil? c)
      result
      (recur rest (conj result (<! c))))))

(spec/fdef init
           :args (spec/keys :req-un [:ivr.config/layers
                                     :ivr.config.layer.load/http-retry-timeout-s
                                     :ivr.config.layer.load/http-retry-delay-s])
           :ret :ivr.config/info)
(defn init [{:as options
             :keys [layers on-success on-error]}]
  (log "debug" "init" options)
  (let [load-chans (mapv #(base/load-layer % options) layers)
        results-chan (all-chans load-chans)]
    (go
      (let [results (<! results-chan)
            config (apply merge (mapv :config results))
            errors (remove nil? (mapv :error results))
            info {:config config :loads results}]
        (if (empty? errors)
          (on-success info)
          (on-error info))))))
