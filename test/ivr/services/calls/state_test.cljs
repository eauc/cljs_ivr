(ns ivr.services.calls.state-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [deftest is testing use-fixtures]]
            [ivr.models.call :as call]
            [ivr.services.calls.state :as state]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.services.calls.state))
   :after (fn [] (stest/unstrument 'ivr.services.calls.state))})


(deftest call-state-service-test

  (testing "state event"
    (let [call (-> (call/info->call {:id "call-id" :time 42})
                   (assoc-in [:state :info] {:info :state})
                   (assoc-in [:state :status] {"status" "state"})
                   (assoc-in [:state :dial-status] {"dial-status" "state"}))
          db (call/db-insert-call {} call)
          deps {:db db :call-time-now 71}
          event {:id (call/id call)
                 :info {:new-info :new-state}
                 :status {"new-state" "new-state"}
                 :dial-status {"new-dial-status" "new-state"}}]


      (testing "simple state update"
        (is (= {:ivr.call/update
                {:id "call-id"
                 :state {:current "Created"
                         :start-time 42
                         :info {:info :state
                                :new-info :new-state}
                         :status {"status" "state"
                                  "new-state" "new-state"}
                         :dial-status {"dial-status" "state"
                                       "new-dial-status" "new-state"}}}}
               (state/call-state-event deps event))))


      (testing "state change"
        (let [event (assoc event :next-state "InProgress")]
          (is (= {:ivr.call/update
                  {:id "call-id"
                   :state {:current "InProgress"
                           :start-time 71
                           :info {:info :state
                                  :new-info :new-state}
                           :status {"status" "state"
                                    "new-state" "new-state"}
                           :dial-status {"dial-status" "state"
                                         "new-dial-status" "new-state"}}}
                  :ivr.ticket/emit {:subject "CALL"
                                    :callId "call-id"
                                    :callTime 42
                                    :state "Created"
                                    :nextState "InProgress"
                                    :time 71
                                    :duration 29}
                  :dispatch-n
                  [[:ivr.call/leave-state {:id "call-id"
                                           :time 71
                                           :from "Created"
                                           :to "InProgress"}]
                   [:ivr.call/enter-state {:id "call-id"
                                           :time 71
                                           :from "Created"
                                           :to "InProgress"}]]}
                 (state/call-state-event deps event))))))))
