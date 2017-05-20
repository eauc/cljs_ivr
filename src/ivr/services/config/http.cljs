(ns ivr.services.config.http
  (:require [cljs.core.async :as async :refer [>!]]
            [cljs.nodejs :as nodejs]
            [ivr.services.config.base :as base :refer [log]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce superagent (nodejs/require "superagent"))

(defn- delayMs [function delay]
  (go
    (<! (async/timeout delay))
    (function)))

(defmethod base/load-layer :http-layer
  [layer {:keys [http-retry-timeout-s http-retry-delay-s]}]
  (let [result (async/chan 1)
        last-error (atom nil)
        stop (atom false)
        on-http-success
        (fn [response]
          (let [config (-> response
                           (aget "body")
                           (js->clj :keywordize-keys true))]
            (reset! stop true)
            (go (>! result {:desc (:path layer)
                            :config config}))))
        start-load
        (fn try-to-load []
          (if-not @stop
            (-> (superagent "GET" (:path layer))
                (.accept "json")
                (.then
                 on-http-success
                 (fn on-http-error [error]
                   (reset! last-error (aget error "message"))
                   (log "warn" "Http load error, retrying..." {:error @last-error})
                   (delayMs try-to-load (* 1000 http-retry-delay-s)))))))
        stop-load
        #(when-not @stop
           (reset! stop true)
           (log "error" "Load http"
                {:desc (:path layer)
                 :config {}
                 :error @last-error}))]
    (start-load)
    (go (first (async/alts!
                [(delayMs stop-load (* 1000 http-retry-timeout-s))
                 result])))))
