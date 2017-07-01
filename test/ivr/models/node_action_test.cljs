(ns ivr.models.node-action-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.call :as call]
            [ivr.models.node-action :as node-action]
            [ivr.models.verbs :as verbs]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node-action))
   :after (fn [] (stest/unstrument 'ivr.models.node-action))})


(defn get-start-action
  [context]
  (filter #(= :ivr.call/start-action (first %))
          (get-in context [:effects :dispatch-n])))

(defn get-state-update
  [context]
  (filter #(= :ivr.call/state (first %))
          (get-in context [:effects :dispatch-n])))


(deftest node-action-model

  (testing "check start action"
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
               (get-start-action (node-action/check-action context))))

        (testing "response has no status => default 200"
          (let [response {}
                context (assoc-in context [:effects :ivr.routes/response] response)]
            (is (= [[:ivr.call/start-action
                     {:call-id "call-id", :action {:node :stat}}]]
                   (get-start-action (node-action/check-action context)))))))


      (testing "fails"
        (testing "node has no stat action"
          (let [params (update params "node" dissoc "stat")
                route {:req #js {"ivr-params" params}}
                context (assoc-in context [:coeffects :event] [:event route])]
            (is (= []
                   (get-start-action (node-action/check-action context))))))

        (testing "response is an error"
          (let [response {:status 404}
                context (assoc-in context [:effects :ivr.routes/response] response)]
            (is (= []
                   (get-start-action (node-action/check-action context))))))

        (testing "not entering node"
          (let [params (assoc params "action" :leave)
                route {:req #js {"ivr-params" params}}
                context (assoc-in context [:coeffects :event] [:event route])]
            (is (= []
                   (get-start-action (node-action/check-action context)))))))))


  (testing "check call state"
    (let [call (call/info->call {:id "call-id"})
          node {"type" "announcement"}
          params {"call" call "node" node}
          route {:req #js {"ivr-params" params}}
          response {:status 200}
          context {:coeffects {:event [:event route]}
                   :effects {:ivr.routes/response response}}]


      (testing "success -> InProgress"
        (testing "enter *"
          (let [params (merge params {"action" :enter})
                route {:req #js {"ivr-params" params}}
                context {:coeffects {:event [:event route]}
                         :effects {:ivr.routes/response response}}]
            (is (= [[:ivr.call/state {:id "call-id", :next-state "InProgress"}]]
                   (get-state-update (node-action/check-action context))))))

        (testing "enter transfersda, no dial"
          (let [node {"type" "transfersda"}
                params (merge params {"node" node "action" :enter})
                route {:req #js {"ivr-params" params}}
                response (verbs/create [{:type :ivr.verbs/hangup}])
                context {:coeffects {:event [:event route]}
                         :effects {:ivr.routes/response response}}]
            (is (= [[:ivr.call/state {:id "call-id", :next-state "InProgress"}]]
                   (get-state-update (node-action/check-action context))))))

        (testing "enter transferqueue, no play"
          (let [node {"type" "transferqueue"}
                params (merge params {"node" node "action" :enter})
                route {:req #js {"ivr-params" params}}
                response (verbs/create [{:type :ivr.verbs/hangup}])
                context {:coeffects {:event [:event route]}
                         :effects {:ivr.routes/response response}}]
            (is (= [[:ivr.call/state {:id "call-id", :next-state "AcdTransferred"}]]
                   (get-state-update (node-action/check-action context)))))))


      (testing "success -> AcdTransferred"
        (let [node {"type" "transferqueue"}
              params (merge params {"node" node "action" :enter})
              route {:req #js {"ivr-params" params}}
              context {:coeffects {:event [:event route]}
                       :effects {:ivr.routes/response response}}]
          (is (= [[:ivr.call/state {:id "call-id", :next-state "AcdTransferred"}]]
                 (get-state-update (node-action/check-action context))))))


      (testing "success -> TransferRinging"

        (testing "enter transfersda"
          (let [node {"type" "transfersda"}
                params (merge params {"node" node "action" :enter})
                route {:req #js {"ivr-params" params}}
                response (verbs/create [{:type :ivr.verbs/dial-number}])
                context {:coeffects {:event [:event route]}
                         :effects {:ivr.routes/response response}}]
            (is (= [[:ivr.call/state {:id "call-id", :next-state "TransferRinging"}]]
                   (get-state-update (node-action/check-action context))))))

        (testing "enter transferlist"
          (let [node {"type" "transferlist"}
                params (merge params {"node" node "action" :enter})
                route {:req #js {"ivr-params" params}}
                response (verbs/create [{:type :ivr.verbs/dial-number}])
                context {:coeffects {:event [:event route]}
                         :effects {:ivr.routes/response response}}]
            (is (= [[:ivr.call/state {:id "call-id", :next-state "TransferRinging"}]]
                   (get-state-update (node-action/check-action context))))))

        (testing "leave transferlist"
          (let [node {"type" "transferlist"}
                params (merge params {"node" node "action" :leave})
                route {:req #js {"ivr-params" params}}
                response (verbs/create [{:type :ivr.verbs/dial-number}])
                context {:coeffects {:event [:event route]}
                         :effects {:ivr.routes/response response}}]
            (is (= [[:ivr.call/state {:id "call-id", :next-state "TransferRinging"}]]
                   (get-state-update (node-action/check-action context)))))))


      (testing "failure"

        (testing "response error"
          (let [response {:status 500}
                context (assoc-in context [:effects :ivr.routes/response] response)]
            (is (= []
                   (get-state-update (node-action/check-action context))))))

        (testing "transferlist, no dial"
          (let [node {"type" "transferlist"}
                params (assoc params "node" node)
                route {:req #js {"ivr-params" params}}
                response (verbs/create [{:type :ivr.verbs/hangup}])
                context {:coeffects {:event [:event route]}
                         :effects {:ivr.routes/response response}}]
            (is (= []
                   (get-state-update (node-action/check-action context))))))

        (testing "leave *"
          (let [params (assoc params "action" :leave)
                route {:req #js {"ivr-params" params}}
                context {:coeffects {:event [:event route]}
                         :effects {:ivr.routes/response response}}]
            (is (= []
                   (get-state-update (node-action/check-action context))))))))))
