(ns ivr.services.config
  (:require [cljs.core.async :as async :refer [<!]]
            [cljs.nodejs :as nodejs]
            [cljs.spec.alpha :as spec]
            [clojure.string :as str]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.routes.url :as url]
            [ivr.services.config.base :as base :refer [log]]
            [ivr.services.config.file]
            [ivr.services.config.http]
            [ivr.services.config.object]
            [ivr.services.config.invalid]
            [ivr.services.routes :as routes]
            [ivr.specs.config]
            [re-frame.core :as re-frame])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(defn- all-chans [chans]
  (go-loop [[c & rest] chans
            result []]
    (if (nil? c)
      result
      (recur rest (conj result (<! c))))))


(spec/fdef init
           :args (spec/cat :options :ivr.config.init/options))
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


(defn- explain-config [config path]
  (let [value (get-in config path)]
    (if (nil? value)
      {}
      (assoc-in {} path value))))


(defn- explain-loads [loads path]
  (mapv (fn [load]
          (update load :config #(explain-config % path))) loads))


(defn explain [config-info {:strs [path]}]
  (if (nil? path)
    config-info
    (let [path-keys (mapv keyword (str/split path #"\."))
          config (:config config-info)
          loads (:loads config-info)]
      {:config (explain-config config path-keys)
       :loads (explain-loads loads path-keys)})))


(defn explain-route [{:keys [db]} {:keys [params]}]
  (let [explanation (explain (:config-info db) params)
        link (url/absolute [:v1 :config :explain])]
    {:ivr.routes/response
     {:data explanation
      :link link}}))

(routes/reg-action
  :ivr.config/explain-route
  explain-route)


(re-frame/reg-cofx
  :ivr.config/cofx
  (fn config-cofx [{:keys [db] :as coeffects} path]
    (->> path
         (concat [:config-info :config])
         (get-in db)
         (assoc coeffects :config))))
