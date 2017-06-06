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
          acd #(assoc % :acd :query)
          verbs (fn [vs] {:verbs :create :data vs})
          deps {:acd acd :verbs verbs}
          call {:info {:id "call-id"
                       :time "call-time"}}
          context {:call call :deps deps}]
      (is (= {:ivr.web/request
              {:acd :query
               :type :ivr.acd/enqueue-call
               :call call
               :node_id "node-id",
               :script_id "script-id",
               :queue_id "queue-id",
               :on-success
               [:ivr.models.node.transfert-queue/play-waiting-sound
                {:node node}],
               :on-error
               [:ivr.models.node.transfert-queue/error-acd-enqueue
                {:node node}]}}
             (node/enter-type node context)))
      (testing "play-waiting-sound"
        (is (= {:ivr.routes/response
                {:verbs :create
                 :data [{:type :ivr.verbs/loop-play
                         :path "/cloudstore/file/waiting"}]}}
               (tq-node/play-waiting-sound
                 deps
                 {:node node
                  :wait-sound "waiting"}))))
      (testing "error-acd-enqueue"
        (is (= {:ivr.routes/response
                {:verbs :create
                 :data [{:type :ivr.verbs/hangup}]}}
               (tq-node/error-acd-enqueue
                 deps
                 {:node node
                  :error {:message "error"}})))))))
