(ns ivr.models.node.voice-record-test
	(:require [cljs.spec.test :as stest]
						[clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[ivr.models.node :as node]
						[ivr.models.node.voice-record :as vr-node]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.node.voice-record))
	 :after (fn [] (stest/unstrument 'ivr.models.node.voice-record))})

(deftest voice-record-node-model
	(testing "conform"
		(is (= {"type" "voicerecord"}
					 (node/conform-type {"type" "voicerecord"})))
		(is (= {"type" "voicerecord"
						"varname" "to_record"
						"case" {}}
					 (node/conform-type {"type" "voicerecord"
															 "varname" "to_record"
															 "case" {}})))
		(is (= {"type" "voicerecord"
						"varname" "to_record"
						"case" {"cancel" "42"
										"validate" {"next" "43"}}}
					 (node/conform-type {"type" "voicerecord"
															 "varname" "to_record"
															 "case" {"cancel" "42"
																			 "validate" "43"}})))
		(is (= {"type" "voicerecord"
						"varname" "to_record"
						"case" {"validate" {"next" "43"
																"set" {:type :ivr.node.preset/set
																			 :to "to_var"
																			 :value "value"}}}}
					 (node/conform-type {"type" "voicerecord"
															 "varname" "to_record"
															 "case" {"validate" {"next" "43"
																									 "set" {"varname" "to_var"
																													"value" "value"}}}})))
		(is (= {"type" "voicerecord"
						"varname" "to_record"
						"case" {"validate" {"set" {:type :ivr.node.preset/copy
																			 :to "to_var"
																			 :from "from_var"}}}}
					 (node/conform-type {"type" "voicerecord"
															 "varname" "to_record"
															 "case" {"validate" {"set" {"varname" "to_var"
																													"value" "$from_var"}}}}))))


	(testing "enter"
		(is (= {:ivr.routes/dispatch
						[:ivr.models.node.voice-record/record-with-config
						 {:node {"type" "voicerecord"}}]}
					 (node/enter-type {"type" "voicerecord"} "context")))
		(let [node {"type" "voicerecord"
								"id" "node-id"
								"script_id" "script-id"
								"validate_key" "4"
								"cancel_key" "5"}
					verbs (fn [vs] {:verbs :create :data vs})
					deps {:verbs verbs}
					config {:maxlength 245}]


			(testing "record-with-config"
				(is (= {:ivr.routes/response
								{:verbs :create
								 :data [{:type :ivr.verbs/record
												 :maxlength 245
												 :finishonkey "45"
												 :callbackurl "/smartccivr/script/script-id/node/node-id/callback"}]}}
							 (vr-node/record-with-config
								 (merge deps {:config config})
								 {:node node}))))))


	(testing "leave"
		(let [verbs (fn [vs] {:verbs :create :data vs})
					deps {:verbs verbs}
					context {:deps deps}
					node {"type" "voicerecord"
								"id" "node-id"
								"script_id" "script-id"
								"finish_key" "4"
								"cancel_key" "5"
								"varname" "record_var"}]


			(testing "cancel"
				(let [params {"record_cause" "digit-a" "record_digits" "435"}
							context (merge context {:params params})]
					(is (= {:ivr.routes/response
									{:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
								 (node/leave-type node context)))
					(let [node (merge node {"case" {"cancel" "42"}})]
						(is (= {:ivr.routes/response
										{:verbs :create
										 :data [{:type :ivr.verbs/redirect
														 :path "/smartccivr/script/script-id/node/42"}]}}
									 (node/leave-type node context))))))


			(testing "validate"
				(let [params {"record_cause" "digit-a"
											"record_digits" "53"
											"record_url" "/record/url"}
							action-data {"action" "data"}
							context (merge context {:call {:action-data action-data
																						 :info {:id "call-id"}}
																			:params params})]
					(is (= {:ivr.call/action-data
									{:info {:id "call-id"}
									 :action-data {"action" "data"
																 "record_var" "/record/url"}}
									:ivr.routes/response
									{:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
								 (node/leave-type node context))))


				(let [params {"record_cause" "hangup"
											"record_digits" "435"
											"record_url" "/record/url"}
							action-data {"action" "data"}
							context (merge context {:call {:action-data action-data
																						 :info {:id "call-id"}}
																			:params params})]
					(let [node (merge node {"case" {"validate" {"next" "44"
																											"set" {:type :ivr.node.preset/copy
																														 :from "record_var"
																														 :to "to_var"}}}})]
						(is (= {:ivr.call/action-data
										{:info {:id "call-id"}
										 :action-data {"action" "data"
																	 "record_var" "/record/url"
																	 "to_var" "/record/url"}}
										:ivr.routes/response
										{:verbs :create, :data [{:type :ivr.verbs/redirect
																						 :path "/smartccivr/script/script-id/node/44"}]}}
									 (node/leave-type node context))))


					(let [node (merge node {"case" {"validate" {"set" {:type :ivr.node.preset/set
																														 :value "set_value"
																														 :to "to_var"}}}})]
						(is (= {:ivr.call/action-data
										{:info {:id "call-id"}
										 :action-data {"action" "data"
																	 "record_var" "/record/url"
																	 "to_var" "set_value"}}
										:ivr.routes/response
										{:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
									 (node/leave-type node context)))))))))
