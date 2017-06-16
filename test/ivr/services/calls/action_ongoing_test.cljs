(ns ivr.services.calls.action-ongoing-test
	(:require [cljs.spec.test :as stest]
						[clojure.test :as test :refer-macros [deftest is testing use-fixtures]]
						[ivr.models.call :as call]
						[ivr.services.calls.action-ongoing :as action-ongoing]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.services.calls.action-ongoing))
	 :after (fn [] (stest/unstrument 'ivr.services.call.action-ongoing))})

(deftest call-action-ongoing-model

	(testing "start-action"
		(let [call (-> (call/info->call {:id "call-id"})
									 (assoc :action-ongoing {:start-time 42
																					 :action {:action :ongoing}}))
					db (call/db-insert-call {} call)
					coeffects {:db db :call-time-now 71}
					event [:event {:call-id "call-id" :action {:node :action}}]]


			(testing "call has an ongoing action, emit ticket"
				(is (= {:ivr.call/action-ongoing
								{:info {:id "call-id"}
								 :action-data {}
								 :action-ongoing {:start-time 71
																	:action {:node :action}}}
								:ivr.ticket/emit
								{:producer "IVR"
								 :subject "ACTION"
								 :callid "call-id"
								 :time 71
								 :duration 29
								 :action {:action :ongoing}}}
							 (action-ongoing/start-action coeffects event))))


			(testing "call has no ongoing action"
				(let [db (call/db-update-call db "call-id" dissoc :action-ongoing)
							coeffects {:db db :call-time-now 71}]
					(is (= {:ivr.call/action-ongoing
									{:info {:id "call-id"}
									 :action-data {}
									 :action-ongoing {:start-time 71
																		:action {:node :action}}}}
								 (action-ongoing/start-action coeffects event)))))


			(testing "call not found"
        (let [db (update db :calls dissoc "call-id")
							coeffects {:db db :call-time-now 71}]
          (is (= {}
                 (action-ongoing/start-action coeffects event))))))))
