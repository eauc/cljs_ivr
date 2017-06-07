(ns ivr.models.acd-test
	(:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[cljs.spec.test :as stest]
						[ivr.models.acd :as acd]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.acd))
	 :after (fn [] (stest/unstrument 'ivr.models.acd))})

(deftest acd-model-test


  (testing "enqueue call"
		(let [request (acd/query {:type :ivr.acd/enqueue-call
                              :call {:info {:id "call-id" :time "call-time"}}
                              :account_id "account-id"
                              :application_id "app-id"
                              :node_id "node-id"
                              :queue_id "queue-id"
                              :script_id "script-id"
                              :to "to"
                              :from "from"
                              :on-success [:success]
                              :on-error [:error]})]
      (is (= {:method "POST"
              :url "/smartccacdlink/call/call-id/enqueue"
              :data {:call_id "call-id"
                     :callTime "call-time"
                     :account_id "account-id"
                     :application_id "app-id"
                     :queue_id "queue-id"
                     :to "to"
                     :from "from"
                     :ivr_fallback "/smartccivr/script/script-id/node/node-id/callback"}
              :on-error [:error]}
             (dissoc request :on-success)))


      (testing "enqueue call success"
        (let [on-success (:on-success request)]
          (is (= [:success {:wait-sound "wait-sound"}]
                 (on-success #js {:body {"waitSound" "wait-sound"}}))))))))
