(ns ivr.models.node.fetch-test
	(:require [cljs.spec.test :as stest]
						[clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[ivr.models.node :as node]
						[ivr.models.node.fetch :as fetch-node]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.node.fetch))
	 :after (fn [] (stest/unstrument 'ivr.models.node.fetch))})

(deftest fetch-node-model
  (testing "enter"
		(let [services #(assoc % :services :query)
					deps {:services services}
					node {"type" "fetch"
								"account_id" "account-id"
								"id_routing_rule" "rule-id"}]
			(is (= {:ivr.web/request
							{:services :query
							 :type :ivr.services/eval-routing-rule
							 :account-id "account-id"
							 :route-id "rule-id"
							 :on-success
							 [:ivr.models.node.fetch/apply-routing-rule
								{:call "call" :node node}]
							 :on-error
							 [:ivr.models.node.fetch/error-routing-rule
								{:call "call" :node node}]}}
						 (node/enter-type node {:call "call" :deps deps})))))


	(testing "apply-routing-rule"
		(let [node {"type" "fetch"
								"account_id" "account-id"
								"id_routing_rule" "rule-id"
								"varname" "to_fetch"}
					call {:info {:id "call-id"}
								:action-data {"action" "data"}}
					deps {:verbs (fn [vs] {:verbs :create :data vs})}
					route-value "route_value"
					context {:node node
									 :call call
									 :route-value route-value}]
			(is (= {:ivr.call/action-data
							{:info {:id "call-id"}
							 :action-data {"action" "data"
                             "to_fetch" "route_value"}}
							:ivr.routes/response
							{:verbs :create
							 :data [{:type :ivr.verbs/hangup}]}}
						 (fetch-node/apply-routing-rule deps context)))))


	(testing "error-routing-rule"
		(let [node {"type" "fetch"
								"account_id" "account-id"
								"script_id" "script-id"
								"id_routing_rule" "rule-id"
								"next" "42"
								"varname" "to_fetch"}
					call {:info {:id "call-id"}
								:action-data {"action" "data"}}
					deps {:verbs (fn [vs] {:verbs :create :data vs})}
					error {:message "rule_error"}
					context {:node node
									 :call call
									 :error error}]
			(is (= {:ivr.call/action-data
							{:info {:id "call-id"}
							 :action-data {"action" "data"
														 "to_fetch" "__FAILED__"}}
							:ivr.routes/response
							{:verbs :create
							 :data [{:type :ivr.verbs/redirect
											 :path "/smartccivr/script/script-id/node/42"}]}}
						 (fetch-node/error-routing-rule deps context))))))
