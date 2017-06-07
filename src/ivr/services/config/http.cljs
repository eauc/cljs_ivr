(ns ivr.services.config.http
  (:require [cljs.core.async :as async]
            [cljs.nodejs :as nodejs]
            [clojure.walk :as walk]
            [ivr.services.config.base :as base :refer [log]]
            [ivr.services.web :as web])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defonce superagent (nodejs/require "superagent"))

(defn with-timeout-ms [channel timeout on-timeout]
  (go
    (first
     (async/alts!
      [(go (<! (async/timeout timeout)) (on-timeout))
       channel]))))

(defmethod base/load-layer :http-layer
  [layer {:keys [http-retry-timeout-s http-retry-delay-s]}]
  (let [last-error (atom nil)
        stop (atom false)
        on-http-success
        (fn [response]
          (let [config (-> (aget response "body")
                           (walk/keywordize-keys))]
            (reset! stop true)
            {:desc (:path layer)
             :config config}))
        result
        (go-loop []
          (if-not @stop
            (let [[result payload] (<! (web/request {:url (:path layer)}))]
              (if (= result :ivr.web/success)
                (on-http-success payload)
                (do
                  (reset! last-error (:message payload))
                  (log "warn" "Http load error, retrying...")
                  (<! (async/timeout (* 1000  http-retry-delay-s)))
                  (recur))))))
        stop-load
        #(when-not @stop
           (reset! stop true)
           (log "error" "Load http"
                {:desc (:path layer)
                 :config {}
                 :error @last-error}))]
    (with-timeout-ms result (* 1000 http-retry-timeout-s) stop-load)))
