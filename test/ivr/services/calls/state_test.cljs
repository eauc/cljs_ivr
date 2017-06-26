(ns ivr.services.calls.state-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [deftest is testing use-fixtures]]
            [ivr.services.calls.state :as state]
            [ivr.models.call :as call]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.services.calls.state))
   :after (fn [] (stest/unstrument 'ivr.services.calls.state))})


(deftest call-state-services

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
                  :ivr.ticket/emit {:state "Created"
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
                 (state/call-state-event deps event)))))))


  (testing "enter-state event"
    (let [call (-> (call/info->call {:id "call-id" :time 42})
                   (assoc-in [:state :info :sda] "sda"))
          db (call/db-insert-call {} call)
          deps {:db db
                :cloudmemory #(assoc % :cloudmemory :query)
                :services #(assoc % :services :query)}
          event {:id (call/id call) :time 71 :from "FromState" :to "InProgress"}]


      (testing "nothing to do"
        (is (= {}
               (state/call-enter-state-event deps event))))


      (testing "* -> Transferred, increment sda limit"
        (let [event (assoc event :to "Transferred")]
          (is (= {:ivr.web/request
                  {:type :ivr.cloudmemory/inc-sda-limit
                   :sda "sda"
                   :cloudmemory :query}}
                 (state/call-enter-state-event deps event)))))


      (testing "* -> Terminated, terminate call"
        (let [event (assoc event :to "Terminated")]
          (is (= {:ivr.call/remove "call-id",
                  :ivr.web/request
                  {:id "call-id"
                   :time 42
                   :type :ivr.services/call-on-end
                   :services :query}}
                 (state/call-enter-state-event deps event)))))))


  (testing "leave-state event"
    (let [call (-> (call/info->call {:id "call-id"
                                     :account-id "account-id"
                                     :time 42})
                   (assoc-in [:state :info :sda] "sda")
                   (assoc-in [:state :status "cause"] "user-hangup"))
          db (call/db-insert-call {} call)
          deps {:db db
                :cloudmemory #(assoc % :cloudmemory :query)
                :acd #(assoc % :acd :query)}
          event {:id (call/id call) :time 71 :from "FromState" :to "InProgress"}]


      (testing "nothing to do"
        (is (= {}
               (state/call-leave-state-event deps event))))


      (testing "Transferred -> *, decrement sda limit"
        (let [event (assoc event :from "Transferred")]
          (is (= {:ivr.web/request
                  {:type :ivr.cloudmemory/dec-sda-limit
                   :sda "sda"
                   :cloudmemory :query}}
                 (state/call-leave-state-event deps event)))))


      (testing "AcdTransferred -> *, update acd status"
        (let [event (assoc event :from "AcdTransferred")]
          (is (= {:ivr.web/request
                  {:type :ivr.acd/update-call-status
                   :account-id "account-id"
                   :call-id "call-id"
                   :status "in-progress"
                   :cause "user-hangup"
                   :IVRStatus {:state "InProgress"
                               :lastChange 71}
                   :acd :query}}
                 (state/call-leave-state-event deps event))))))))
