(ns ivr.models.call-state-test
	(:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[cljs.spec.test :as stest]
						[ivr.models.call :as call]
						[ivr.models.call-state :as call-state]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.call-state))
	 :after (fn [] (stest/unstrument 'ivr.models.call-state))})

(deftest call-model-test

	(testing "on-enter"

		(testing "Transferred, increment sda limit"
			(let [call (-> (call/info->call {:id "call-id" :time 42})
										 (assoc-in [:state :info :sda] "sda"))
						event {:id (call/id call) :time 71
									 :from "FromState" :to "Transferred"
									 :cloudmemory #(assoc % :cloudmemory :query)
									 :services #(assoc % :services :query)}]
				(is (= {:ivr.web/request
								{:type :ivr.cloudmemory/inc-sda-limit
								 :sda "sda"
								 :cloudmemory :query}}
							 (call-state/on-enter call event)))))


		(testing "Terminated, terminate call"
			(let [call (-> (call/info->call {:id "call-id"
																			 :account-id "account-id"
																			 :application-id "app-id"
																			 :script-id "script-id"
																			 :from "from"
																			 :to "to"
																			 :time 42})
										 (assoc :action-data {:action :data})
										 (assoc :action-ongoing {:action :ongoing
																						 :start-time 14}))
						options {:from "Transferred"
										 :to "Terminated"
										 :services #(assoc % :services :query)
										 :time 71}]

				(testing "from AcdTransferred"
					(let [options (assoc options :from "AcdTransferred")
								expected-ticket {:producer "IVR"
																 :time 71
																 :duration 57
																 :applicationid "app-id"
																 :from "from"
																 :callTime 42
																 :callid "call-id"
																 :action :ongoing
																 :accountid "account-id"
																 :subject "ACTION"
																 :scriptid "script-id"
																 :to "to"}]
						(is (= (assoc expected-ticket :endCause "")
									 (get (call-state/on-enter
													(assoc-in call [:state :info :overflow-cause] "overflow-cause")
													options)
												:ivr.ticket/emit)))
						(is (= (assoc expected-ticket :endCause "")
									 (get (call-state/on-enter
													(assoc-in call [:state :status "cause"] "xml-hangup")
													options)
												:ivr.ticket/emit)))
						(is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
									 (get (call-state/on-enter
													(-> call
															(assoc-in [:state :info :overflow-cause] "overflow-cause")
															(assoc-in [:state :status "cause"] "xml-hangup"))
													options)
												:ivr.ticket/emit)))))

				(testing "from InProgress"
					(let [options (assoc options :from "InProgress")
								expected-ticket {:producer "IVR"
																 :time 71
																 :duration 57
																 :applicationid "app-id"
																 :from "from"
																 :callTime 42
																 :callid "call-id"
																 :action :ongoing
																 :accountid "account-id"
																 :subject "ACTION"
																 :scriptid "script-id"
																 :to "to"}]
						(is (= (assoc expected-ticket :endCause "CALLER_HANG_UP")
									 (get (call-state/on-enter
													(assoc-in call [:state :status "cause"] "user-hangup")
													options)
												:ivr.ticket/emit)))
						(is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
									 (get (call-state/on-enter
													(assoc-in call [:state :status "cause"] "xml-hangup")
													options)
												:ivr.ticket/emit)))))

				(testing "from Transferred"
					(is (= {:ivr.call/remove "call-id"
									:ivr.web/request {:services :query
																		:type :ivr.services/call-on-end
																		:action :data
																		:account-id "account-id"
																		:application-id "app-id"
																		:id "call-id"
																		:script-id "script-id"
																		:from "from"
																		:to "to"
																		:time 42}}
								 (call-state/on-enter call options))))

				(testing "from TransferRinging"
					(let [call (assoc-in call [:state :status "cause"] "user-hangup")
								options (assoc options :from "TransferRinging")
								expected-ticket {:producer "IVR"
																 :time 71
																 :duration 57
																 :applicationid "app-id"
																 :from "from"
																 :callTime 42
																 :callid "call-id"
																 :action :ongoing
																 :accountid "account-id"
																 :subject "ACTION"
																 :scriptid "script-id"
																 :to "to"}]
						(is (= nil
									 (get (call-state/on-enter
													(assoc-in call [:state :status "cause"] "xml-hangup")
													options)
												:ivr.ticket/emit)))
						(is (= (assoc expected-ticket :endCause "CALLER_HANG_UP")
									 (get (call-state/on-enter
													call
													options)
												:ivr.ticket/emit)))
						(is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
									 (get (call-state/on-enter
													(assoc-in call [:state :dial-status "dialstatus"] "failed")
													options)
												:ivr.ticket/emit)))
						(is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
									 (get (call-state/on-enter
													(assoc-in call [:state :dial-status "dialstatus"] "no-answer")
													options)
												:ivr.ticket/emit)))
						(is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
									 (get (call-state/on-enter
													(assoc-in call [:state :dial-status "dialstatus"] "busy")
													options)
												:ivr.ticket/emit))))))))

  (testing "on leave"
    (let [call (-> (call/info->call {:id "call-id"
                                     :account-id "account-id"
                                     :time 42})
                   (assoc-in [:state :info :sda] "sda")
                   (assoc-in [:state :status "cause"] "user-hangup"))
          event {:id (call/id call) :time 71
                 :from "FromState" :to "ToState"
                 :cloudmemory #(assoc % :cloudmemory :query)
                 :acd #(assoc % :acd :query)}]


      (testing "Transferred, decrement sda limit"
        (let [event (assoc event :from "Transferred")]
          (is (= {:ivr.web/request
                  {:type :ivr.cloudmemory/dec-sda-limit
                   :sda "sda"
                   :cloudmemory :query}}
                 (call-state/on-leave call event)))))


      (testing "AcdTransferred, update acd status"
        (let [event (assoc event :from "AcdTransferred")]
          (is (= {:ivr.web/request
                  {:type :ivr.acd/update-call-status
                   :account-id "account-id"
                   :call-id "call-id"
                   :status "in-progress"
                   :cause "user-hangup"
                   :IVRStatus {:state "ToState"
                               :lastChange 71}
                   :acd :query}}
                 (call-state/on-leave call event))))))))
