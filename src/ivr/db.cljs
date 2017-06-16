(ns ivr.db
	(:require [cljs.spec :as spec]
						[clojure.data :as data]
						[ivr.debug :refer [debug?]]
						[ivr.libs.logger :as logger]
						[ivr.specs.db]
						[re-frame.core :as re-frame]
						[re-frame.loggers :as re-loggers]))

(def log
	(logger/create "db"))


(def log-reframe
	(logger/create "reframe"))


(def check-db-interceptor
	(re-frame/->interceptor
		:id ::check-db
		:after
		(fn check-db-schema [context]
			(let [new-db (get-in context [:effects :db])]
				(if (or (nil? new-db) (spec/valid? :ivr.db/db new-db))
					context
					(do
						(log "error" "spec check failed"
								 (spec/explain-data :ivr.db/db new-db))
						(assoc context :effects {})))))))


(defn log-reframe-wrapper
	[level]
	(fn [& args]
		(loop [[x & xs] args
					 msg ""]
			(if-not (string? x)
				(log-reframe level msg x)
				(recur xs (str msg " " x))))))


(re-loggers/set-loggers!
	{:log (fn [& args] (js/console.log.apply js/console (clj->js args)))
	 :warn (log-reframe-wrapper "warn")
	 :error (log-reframe-wrapper "error")
	 :group (fn [& args] (js/console.group.apply js/console (clj->js args)))
	 :groupEnd (fn [& args] (js/console.groupEnd.apply js/console (clj->js args)))})


(def default-interceptors
	[(when debug? re-frame/debug)
	 (when debug? check-db-interceptor)])


(def default-db
	{:calls {}})


(re-frame/reg-event-db
	::init
	[default-interceptors]
	(fn init [_ [_ {:keys [config-info]}]]
		(assoc default-db :config-info config-info)))
