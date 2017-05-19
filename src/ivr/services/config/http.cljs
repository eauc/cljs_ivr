(ns ivr.services.config.http
  (:require [cljs.nodejs :as nodejs]
            [ivr.services.config.base :as base :refer [log]]))

(defonce superagent (nodejs/require "superagent"))

(defn- delayMs [function delay]
  (js/Promise.
   (fn [resolve]
     (js/setTimeout #(resolve (function)) delay))))

(defmethod base/load-layer :http-layer
  [layer {:keys [http-retry-timeout-s http-retry-delay-s]}]
  (let [last-error (atom nil)
        stop (atom false)
        start-load (fn try-to-load []
                     (if-not @stop
                       (-> (superagent "GET" (:path layer))
                           (.accept "json")
                           (.then
                            (fn on-success [response]
                              (let [config (-> response
                                               (aget "body")
                                               (js->clj :keywordize-keys true))]
                                (reset! stop true)
                                {:desc (:path layer)
                                 :config config}))
                            (fn on-error [error]
                              (reset! last-error (aget error "message"))
                              (log "warn" "Http load error, retrying..." {:error @last-error})
                              (delayMs try-to-load (* 1000 http-retry-delay-s)))))))
        stop-load (fn []
                    (when-not @stop
                      (reset! stop true)
                      (log "error" "Load http"
                           {:desc (:path layer)
                            :config {}
                            :error @last-error})))]
    (.race js/Promise #js [(delayMs stop-load (* 1000 http-retry-timeout-s))
                           (start-load)])))
