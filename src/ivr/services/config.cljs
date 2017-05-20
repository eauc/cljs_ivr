(ns ivr.services.config
  (:require [clojure.string :as str]
            [cljs.core.async :as async :refer [<!]]
            [cljs.nodejs :as nodejs]
            [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.services.config.base :as base :refer [log]]
            [ivr.services.config.file]
            [ivr.services.config.http]
            [ivr.services.config.object]
            [ivr.services.config.invalid]
            [re-frame.core :as re-frame])
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

(defn explain-config [config path]
  (let [value (get-in config path)]
    (if (nil? value)
      {}
      (assoc-in {} path value))))

(defn explain-loads [loads path]
  (mapv (fn [load]
          (update load :config #(explain-config % path))) loads))

(defn explain [config-info {:keys [path]}]
  (if (nil? path)
    config-info
    (let [path-keys (mapv keyword (str/split path #"\."))
          config (:config config-info)
          loads (:loads config-info)]
      {:config (explain-config config path-keys)
       :loads (explain-loads loads path-keys)})))

(re-frame/reg-event-fx
 ::explain-route
 ;; db/default-interceptors
 (fn config-explain-route [{:keys [db]} [_ req res]]
   (let [query (js->clj (aget req "query") :keywordize-keys true)
         explanation (explain (:config-info db) query)
         response (merge explanation {:link "/config"})]
     (-> res (.send (clj->js response))))
   {}))
