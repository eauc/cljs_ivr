(ns ivr.models.node.transfert-queue-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.node :as node]
            [ivr.models.node.transfert-queue :as tq-node]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node.transfert-queue))
   :after (fn [] (stest/unstrument 'ivr.models.node.transfert-queue))})

(deftest transfert-queue-node
  (testing "enter"
    (let [node {:type "transferqueue"
                :id "node-id"
                :script-id "script-id"
                :queue "queue-id"}
          verbs (fn [vs] {:verbs :create :data vs})
          options {:call-id "call-id"
                   :call-time "call-time"
                   :verbs verbs}]
      (is (= {:ivr.web/request
              {:method "POST"
               :url "/smartccacdlink/call/call-id/enqueue"
               :data
               {:queue_id "queue-id"
                :ivr_fallback "/smartccivr/script/script-id/node/node-id/callback",
                :callTime "call-time"},
               :on-success
               [:ivr.models.node.transfert-queue/play-waiting-sound
                {:options options}]
               :on-error
               [:ivr.models.node.transfert-queue/error-acd-enqueue
                {:node node, :options options}]}}
             (node/enter-type node options)))
      (testing "play-waiting-sound"
        (let [response #js {:body {:waitSound "waiting"}}]
          (is (= {:ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/loop-play
                           :path "/cloudstore/file/waiting"}]}}
                 (tq-node/play-waiting-sound
                  {} [:event {:options options
                              :response response}])))))
      (testing "error-acd-enqueue"
        (is (= {:ivr.routes/response
                {:verbs :create
                 :data [{:type :ivr.verbs/hangup}]}}
               (tq-node/error-acd-enqueue
                {} [:event {:options options}])))))))
