(ns ivr.models.node.transfert-sda-test
  (:require [cljs.spec.test.alpha :as stest]
            [clojure.test :as test :refer-macros [deftest is testing use-fixtures]]
            [ivr.models.call :as call]
            [ivr.models.node :as node]
            [ivr.models.node.transfert-sda :as ts-node]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.node.transfert-sda))
	 :after (fn [] (stest/unstrument 'ivr.models.node.transfert-sda))})

(deftest transfert-sda-node-model
	(testing "conform"
		(is (= {"type" "transfersda"}
					 (node/conform-type {"type" "transfersda"})))
		(is (= {"type" "transfersda"
						"case" {"busy" "42"}}
					 (node/conform-type {"type" "transfersda"
															 "case" {"busy" "42"}})))
    (is (= {"type" "transfersda"
						"case" {"no-answer" "42"}}
					 (node/conform-type {"type" "transfersda"
															 "case" {"noanswer" "42"}})))
    (is (= {"type" "transfersda"
						"case" {"no-answer" "42"}}
					 (node/conform-type {"type" "transfersda"
															 "case" {"no-answer" "42"}}))))


  (let [node {"type" "transfersda"
							"id" "node-id"
							"account_id" "account-id"
							"script_id" "script-id"
							"dest" "dest-sda"}
				store #(assoc % :store :query)
				verbs (fn [vs] {:verbs :create :data vs})
				deps {:store store
							:verbs verbs}
				context {:deps deps}]


		(testing "enter"
			(is (= {:ivr.web/request
							{:store :query
							 :type :ivr.store/get-account
							 :id "account-id"
							 :on-success
							 [:ivr.models.node.transfert-sda/transfert-sda-with-config
								{:node node}]
							 :on-error
							 [:ivr.models.node.transfert-sda/transfert-sda-with-config
								{:node node}]}}
						 (node/enter-type node context)))


      (testing "transfert-sda-with-config"
				(let [config {:fromSda "CALLEE"
											:ringingTimeoutSec 15}
							account {"fromSda" "CALLER"
											 "record_enabled" true
											 "ringing_tone" "ringing"}
              call (-> (call/info->call {:id "call-id" :time "call-time"})
                       (assoc-in [:state :current] "TransferRinging")
                       (assoc-in [:state :info :sda] "failed-sda"))
							params {"call" call
                      "from" "from-number"
											"to" "to-number"}]
					(is (= {:dispatch-n
                  [[:ivr.call/state {:id "call-id", :info {:sda "dest-sda"
                                                           :failed-sda "failed-sda"}}]]
                  :ivr.routes/response
									{:verbs :create
									 :data [{:type :ivr.verbs/dial-number
													 :number "dest-sda"
													 :from "from-number"
													 :timeout 15
													 :record true
													 :callbackurl "/smartccivr/script/script-id/node/node-id/callback"
													 :statusurl "/smartccivr/script/script-id/dialstatus"
													 :waitingurl "/smartccivr/twimlets/loopPlay/ringing"}]}}
								 (ts-node/transfert-sda-with-config
									 (merge deps {:config config})
									 {:node node :account account}
									 {:params params}))))))


		(testing "leave"
			(let [node (merge node {"next" "42"})
						params {"dialstatus" "completed"}
						context (assoc context :params params)]
				(is (= {:ivr.routes/response
								{:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
							 (node/leave-type node context))))


			(let [node (merge node {"case" {"busy" "42"
																			"no-answer" "71"}})
						params {"dialstatus" "busy"}
						context (assoc context :params params)]
				(is (= {:ivr.routes/response
								{:verbs :create,
								 :data
								 [{:type :ivr.verbs/redirect,
									 :path "/smartccivr/script/script-id/node/42"}]}}
							 (node/leave-type node context))))


			(let [node (merge node {"case" {"busy" "42"
																			"no-answer" "71"}})
						params {"dialstatus" "no-answer"}
						context (assoc context :params params)]
				(is (= {:ivr.routes/response
								{:verbs :create,
								 :data
								 [{:type :ivr.verbs/redirect,
									 :path "/smartccivr/script/script-id/node/71"}]}}
							 (node/leave-type node context))))


			(let [node (merge node {"case" {"busy" "42"
																			"other" "71"}})
						params {"dialstatus" "no-answer"}
						context (assoc context :params params)]
				(is (= {:ivr.routes/response
								{:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
							 (node/leave-type node context))))


			(let [node (merge node {"case" {"busy" "42"
																			"other" "71"}})
						params {"dialstatus" "failed"}
						context (assoc context :params params)]
				(is (= {:ivr.routes/response
								{:verbs :create,
								 :data
								 [{:type :ivr.verbs/redirect,
									 :path "/smartccivr/script/script-id/node/71"}]}}
							 (node/leave-type node context)))))))
