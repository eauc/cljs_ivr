(ns ivr.models.node.dtmf-catch-test
	(:require [cljs.spec.test :as stest]
						[clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[ivr.models.node :as node]
						[ivr.models.node.dtmf-catch :as dtmf-catch-node]
						[ivr.models.node-set :as node-set]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.node.dtmf-catch))
	 :after (fn [] (stest/unstrument 'ivr.models.node.dtmf-catch))})

(deftest dtmf-catch-node-model
	(let [options {:id "node-id"
								 :account-id "account-id"
								 :script-id "script-id"}]

		(testing "conform"
			(is (= {"type" "dtmfcatch"
							"id" "node-id"
							"account_id" "account-id"
							"script_id" "script-id"
							"max_attempts" 0
							"retry" 0
							"welcome" [{:type :ivr.node.dtmf-catch/speak
													"varname" "son1"
													"voice" "alice"
													"pronounce" "normal"}
												 {:type :ivr.node.dtmf-catch/sound
													"soundname" "son1"}
												 {:type :ivr.node.dtmf-catch/speak
													"varname" "toto"
													"voice" "thomas"
													"pronounce" "phone"}]}
						 (node/conform
							 {"type" "dtmfcatch"
								"welcome" [{"varname" "son1"
														"voice" "alice"}
													 {"soundname" "son1"}
													 {"varname" "toto"
														"voice" "thomas"
														"pronounce" "phone"}]}
							 options)))


			(testing ":welcome is nil"
				(is (= {"type" "dtmfcatch"
								"id" "node-id"
								"account_id" "account-id"
								"script_id" "script-id"
								"max_attempts" 5
								"retry" 0
								"welcome" []}
							 (node/conform
								 {"type" "dtmfcatch"
									"max_attempts" "5"}
								 options))))


			(testing "case.dtmf_ok"
				(is (= {"dtmf_ok" {"next" "42"}}
							 (get
								 (node/conform
									 {"type" "dtmfcatch"
										"case" {"dtmf_ok" "42"}}
									 options)
								 "case")))
				(is (= {"dtmf_ok" {"next" "42"
													 "set" [{:to "set_var" :value "set_value"}
																	{:to "copy_to" :from "copy_from"}]}}
							 (get
								 (node/conform
									 {"type" "dtmfcatch"
										"case" {"dtmf_ok" {"next" "42"
																			 "set" [{"varname" "set_var" "value" "set_value"}
																							{"varname" "copy_to" "value" "$copy_from"}]}}}
									 options)
								 "case")))))


		(let [base-node {"type" "dtmfcatch"
										 "id" "node-id"
										 "account_id" "account-id"
										 "script_id" "script-id"
										 "max_attempts" 1
										 "retry" 0
										 "varname" "to_var"
										 "welcome" []}
					store #(assoc % :store :query)
					verbs (fn [vs] {:verbs :create :data vs})
					deps {:store store
								:verbs verbs}
					call {:action-data {"action" "data"}
								:info {:id "call-id"}}
					context {:call call
									 :deps deps
									 :params {}}]


			(testing "enter"
				(testing "empty welcome"
					(is (= {:ivr.routes/response
									{:verbs :create,
									 :data [{:type :ivr.verbs/gather
													 :numdigits -1
													 :callbackurl "/smartccivr/script/script-id/node/node-id/callback?retries=1"
													 :play []}
													{:type :ivr.verbs/redirect,
													 :path "/smartccivr/script/script-id/node/node-id/callback?retries=1"}]}}
								 (node/enter-type base-node context))))


				(testing "some speak sounds"
					(is (= {:ivr.routes/response
									{:verbs :create
									 :data [{:type :ivr.verbs/gather
													 :numdigits -1
													 :callbackurl "/smartccivr/script/script-id/node/node-id/callback?retries=1",
													 :play [{:text "key", :voice "alice"}
																	{:text "04.", :voice "alice"}
																	{:text "78.", :voice "alice"}
																	{:text "59.", :voice "alice"}
																	{:text "71.", :voice "alice"}
																	{:text "06.", :voice "alice"}]}
													{:type :ivr.verbs/redirect
													 :path "/smartccivr/script/script-id/node/node-id/callback?retries=1"}]}}
								 (node/enter-type
									 (merge base-node {"welcome" [{:type :ivr.node.dtmf-catch/speak
																								 "varname" "titi"
																								 "voice" "alice"
																								 "pronounce" "normal"}
																								{:type :ivr.node.dtmf-catch/speak
																								 "varname" "toto"
																								 "voice" "alice"
																								 "pronounce" "phone"}]})
									 (update-in context [:call :action-data] merge {"titi" "key"
																																	"toto" "+33478597106"})))))


				(testing "store request for file sounds, current progress is saved in success event"
					(let [node (merge base-node
														{"welcome" [{:type :ivr.node.dtmf-catch/speak
																				 "varname" "titi"
																				 "voice" "alice"
																				 "pronounce" "normal"}
																				{:type :ivr.node.dtmf-catch/sound
																				 "soundname" "son1"}
																				{:type :ivr.node.dtmf-catch/speak
																				 "varname" "toto"
																				 "voice" "alice"
																				 "pronounce" "normal"}]})
								context (merge context {:call {:action-data {"titi" "hey"
																														 "toto" "bye"}}})]
						(is (= {:ivr.web/request
										{:store :query
										 :type :ivr.store/get-sound-by-name
										 :name "son1"
										 :account-id "account-id"
										 :script-id "script-id"
										 :on-success [:ivr.models.node.dtmf-catch/sound-name-success
																	{:retries 1
																	 :node node
																	 :call (:call context)
																	 :loaded [{:text "hey", :voice "alice"}]
																	 :rest [{:type :ivr.node.dtmf-catch/speak
																					 "varname" "toto"
																					 "voice" "alice"
																					 "pronounce" "normal"}]}]}}
									 (node/enter-type node context)))))


				(testing "gather parameters extracted from node"
					(is (= {:ivr.routes/response
									{:verbs :create
									 :data [{:type :ivr.verbs/gather
													 :finishonkey "42"
													 :numdigits 5
													 :timeout 4
													 :callbackurl "/smartccivr/script/script-id/node/node-id/callback?retries=1",
													 :play [{:text "key", :voice "alice"}]}
													{:type :ivr.verbs/redirect
													 :path "/smartccivr/script/script-id/node/node-id/callback?retries=1"}]}}
								 (node/enter-type
									 (merge base-node {"finishonkey" "42"
																		 "numdigits" 5
																		 "timeout" 4
																		 "welcome" [{:type :ivr.node.dtmf-catch/speak
																								 "varname" "titi"
																								 "voice" "alice"
																								 "pronounce" "normal"}]})
									 (merge context {:call {:action-data {"titi" "key"}}}))))))


			(testing "play-sound-event"
				(let [call {:action-data {"titi" "hey"
																	"toto" "bye"}}]


					(testing "continue from saved progress, only speak sounds left, response"
						(is (= {:ivr.routes/response
										{:verbs :create
										 :data [{:type :ivr.verbs/gather
														 :numdigits -1
														 :callbackurl "/smartccivr/script/script-id/node/node-id/callback?retries=1"
														 :play [{:text "hey", :voice "alice"}
																		"/url/son1"
																		{:text "bye", :voice "alice"}]}
														{:type :ivr.verbs/redirect,
														 :path "/smartccivr/script/script-id/node/node-id/callback?retries=1"}]}}
									 (dtmf-catch-node/sound-name-success
										 deps
										 {:sound-url "/url/son1"
											:retries 1
											:node base-node
											:call call
											:loaded [{:text "hey", :voice "alice"}]
											:rest [{:type :ivr.node.dtmf-catch/speak
															"varname" "toto"
															"voice" "alice"
															"pronounce" "normal"}]}))))


					(testing "continue from saved progress, another request for file sound"
						(is (= {:ivr.web/request
										{:store :query
										 :type :ivr.store/get-sound-by-name
										 :name "son2"
										 :account-id "account-id"
										 :script-id "script-id"
										 :on-success [:ivr.models.node.dtmf-catch/sound-name-success
																	{:retries 3
																	 :node base-node
																	 :call call
																	 :loaded ["/url/son1"
																						{:text "bye" :voice "alice"}]
																	 :rest nil}]}}
									 (dtmf-catch-node/sound-name-success
										 deps
										 {:sound-url "/url/son1"
											:retries 3
											:node base-node
											:call call
											:loaded []
											:rest [{:type :ivr.node.dtmf-catch/speak
															"varname" "toto"
															"voice" "alice"
															"pronounce" "normal"}
														 {:type :ivr.node.dtmf-catch/sound
															"soundname" "son2"}]}))))))


			(testing "leave"
				(let [node (merge base-node {"finishonkey" "2"
																		 "numdigits" 2
																		 "validationpattern" "^[421]$"})]


					(testing "success"
						(let [context (merge context {:params {"digits" ["4" "2"]
																									 "termdigit" "2"}})]
							(is (= {:ivr.call/action-data
											{:info {:id "call-id"}
											 :action-data {"action" "data" "to_var" ["4" "2"]}}
											:ivr.routes/response
											{:verbs :create
											 :data [{:type :ivr.verbs/hangup}]}}
										 (node/leave-type node context)))


							(testing "dtmf_ok.set"
								(let [node (merge node {"case" {"dtmf_ok" {"set" [(node-set/map->SetEntry
																																		{:to "set_var"
																																		 :value "set_value"})]}}})]
									(is (= {:info {:id "call-id"}
													:action-data {"action" "data"
																				"to_var" ["4" "2"]
																				"set_var" "set_value"}}
												 (get (node/leave-type node context)
															:ivr.call/action-data)))))


							(testing "dtmf_ok.next"
								(is (= {:verbs :create
												:data [{:type :ivr.verbs/redirect
																:path "/smartccivr/script/script-id/node/42"}]}
											 (get (node/leave-type
															(merge node {"case" {"dtmf_ok" {"next" "42"}}})
															context)
														:ivr.routes/response))))))


					(testing "retry"
						(let [node (merge node {"max_attempts" 1
																		"case" {"max_attempt_reached" "42"}})]
							(testing "invalid numdigits"
								(let [context (merge context {:params {"digits" ["2"]
																											 "termdigit" "2"
																											 "retries" 1}})]
									(is (= {:ivr.routes/response
													{:verbs :create
													 :data [{:type :ivr.verbs/redirect
																	 :path "/smartccivr/script/script-id/node/42"}]}}
												 (node/leave-type node context)))))


							(testing "invalid finishonkey"
								(let [context (merge context {:params {"digits" ["2" "4"]
																											 "termdigit" "4"
																											 "retries" 1}})]
									(is (= {:ivr.routes/response
													{:verbs :create
													 :data [{:type :ivr.verbs/redirect
																	 :path "/smartccivr/script/script-id/node/42"}]}}
												 (node/leave-type node context)))))


							(testing "invalid pattern"
								(let [context (merge context {:params {"digits" ["5" "2"]
																											 "termdigit" "2"
																											 "retries" 1}})]
									(is (= {:ivr.routes/response
													{:verbs :create
													 :data [{:type :ivr.verbs/redirect
																	 :path "/smartccivr/script/script-id/node/42"}]}}
												 (node/leave-type node context))))))


						(let [context (merge context {:params {:digits ["5" "2"]
																									 :termdigit "2"}})]
							(testing "max retries, no next"
								(is (= {:ivr.routes/response
												{:verbs :create
												 :data [{:type :ivr.verbs/hangup}]}}
											 (node/leave-type
												 (merge node {"max_attempts" 1})
												 (merge context {:params {"retries" 1}}))))
								(is (= {:ivr.routes/response
												{:verbs :create
												 :data [{:type :ivr.verbs/hangup}]}}
											 (node/leave-type
												 (merge node {"max_attempts" 5})
												 (merge context {:params {"retries" 5}})))))


							(testing "retry possible"
								(is (= {:ivr.routes/response
												{:verbs :create
												 :data [{:numdigits 2
																 :finishonkey "2"
																 :type :ivr.verbs/gather
																 :callbackurl "/smartccivr/script/script-id/node/node-id/callback?retries=5"
																 :play [{:text "hey", :voice "alice"}]}
																{:type :ivr.verbs/redirect
																 :path "/smartccivr/script/script-id/node/node-id/callback?retries=5"}]}}
											 (node/leave-type
												 (merge node {"max_attempts" 5
																			"welcome" [{:type :ivr.node.dtmf-catch/speak
																									"varname" "toto"
																									"voice" "alice"
																									"pronounce" "normal"}]})
												 (merge context {:params {"retries" 4}
																				 :call {:action-data {"toto" "hey"}}}))))))))))))
