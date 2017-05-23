(ns ivr.services.web
	(:require [cljs.core.async :as async :refer [>!]]
						[cljs.nodejs :as nodejs]
						[ivr.libs.logger :as logger]
						[re-frame.core :as re-frame])
	(:require-macros [cljs.core.async.macros :refer [go]]))

(defonce superagent (nodejs/require "superagent"))

(def log
	(logger/create "web"))

(defn request [{:as description
								:keys [method url accept]
								:or {method "GET"
										 accept "json"}}]
	(let [result (async/chan 1)]
		(log "debug" "start" description)
		(-> (superagent method url)
				(.accept accept)
				(.then
					(fn [response]
						(log "info" "request success"
								 {:method method
									:url url
									:status (aget response "status")})
						(go (>! result [:ivr.web/success response])))
					(fn [error]
						(go (>! result [:ivr.web/error (log "warn" "request failed"
																								{:method method
																								 :url url
																								 :message (aget error "message")})])))))
		result))

(defn- response->event [[success? result]
												[event-success payload-success]
												[event-error payload-error]]
	(if (= :ivr.web/success success?)
		[event-success (assoc payload-success :response result)]
		[event-error (assoc payload-error :error result)]))

(defn- web-request-fx-run [{:as description
														:keys [url on-success on-error]}
                           dispatch_url
													 {:keys [config insert-route-in-event]}]
	(go
		(let [absolute-url (str dispatch_url url)
					response (-> description
											 (assoc :url absolute-url)
											 request
											 <!)]
			(->
				response
				(response->event on-success on-error)
				(insert-route-in-event)
				re-frame/dispatch))))

(defn request-fx [value {:keys [config] :as options}]
	(let [descriptions (if (vector? value) value [value])
        dispatch_url (get-in config [:environment :dispatch_url :internal])]
		(mapv #(web-request-fx-run % dispatch_url options) descriptions)))
