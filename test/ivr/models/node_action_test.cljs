(ns ivr.models.node-action-test
	(:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[cljs.spec.test :as stest]
						[ivr.models.call :as call]
						[ivr.models.node-action :as node-action]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.node-action))
	 :after (fn [] (stest/unstrument 'ivr.models.node-action))})

(deftest node-action-model

	(testing "check-node-enter"
		(let [call (call/info->call {:id "call-id"})
					node {"stat" {:node :stat}}
					params {"action" :enter "call" call "node" node}
					route {:req #js {"ivr-params" params}}
					response {:status 200}
					context {:coeffects {:event [:event route]}
									 :effects {:ivr.routes/response response}}]


      (testing "success"
				(is (= [[:ivr.call/start-action
								 {:call-id "call-id", :action {:node :stat}}]]
							 (get-in (node-action/check-node-enter context)
											 [:effects :dispatch-n])))

        (testing "response has no status => default 200"
          (let [response {}
                context (assoc-in context [:effects :ivr.routes/response] response)]
            (is (= [[:ivr.call/start-action
                     {:call-id "call-id", :action {:node :stat}}]]
                   (get-in (node-action/check-node-enter context)
                           [:effects :dispatch-n])))))

        (testing "append to dispatch"
          (let [context (assoc-in context [:effects :dispatch-n] [[:other-event :payload]])]
            (is (= [[:other-event :payload]
                    [:ivr.call/start-action
                     {:call-id "call-id", :action {:node :stat}}]]
                   (get-in (node-action/check-node-enter context)
                           [:effects :dispatch-n]))))))


      (testing "fails"
        (testing "node has no stat action"
          (let [params (update params "node" dissoc "stat")
                route {:req #js {"ivr-params" params}}
                context (assoc-in context [:coeffects :event] [:event route])]
            (is (= nil
                   (get-in (node-action/check-node-enter context)
                           [:effects :dispatch-n])))))

        (testing "response is an error"
          (let [response {:status 404}
                context (assoc-in context [:effects :ivr.routes/response] response)]
            (is (= nil
                   (get-in (node-action/check-node-enter context)
                           [:effects :dispatch-n])))))

        (testing "not entering node"
          (let [params (assoc params "action" "leave")
                route {:req #js {"ivr-params" params}}
                context (assoc-in context [:coeffects :event] [:event route])]
            (is (= nil
                   (get-in (node-action/check-node-enter context)
                           [:effects :dispatch-n])))))))))
