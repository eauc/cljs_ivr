(ns ivr.models.call-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.call :as call]
            [ivr.models.call-state :as call-state]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.call))
   :after (fn [] (stest/unstrument 'ivr.models.call))})

(deftest call-model-test

  (testing "update-sda"
    (let [call (call/info->call {:id "call-id" :time "call-time"})]
      (is (= [:ivr.call/state {:id "call-id", :info {:sda "new-sda"}}]
             (call/update-sda call "new-sda"))))
    (let [call (-> (call/info->call {:id "call-id" :time "call-time"})
                   (assoc-in [:state :info :sda] "old-sda"))]
      (is (= [:ivr.call/state {:id "call-id", :info {:sda "new-sda"}}]
             (call/update-sda call "new-sda"))))
    (let [call (-> (call/info->call {:id "call-id" :time "call-time"})
                   (assoc-in [:state :current] "TransferRinging")
                   (assoc-in [:state :info :sda] "old-sda"))]
      (is (= [:ivr.call/state {:id "call-id", :info {:sda "new-sda"
                                                     :failed-sda "old-sda"}}]
             (call/update-sda call "new-sda")))))


  (testing "call->action-ticket"
    (let [call (-> (call/info->call {:id "call-id"
                                     :account-id "account-id"
                                     :application-id "application-id"
                                     :from "from-sda"
                                     :to "to-sda"
                                     :script-id "script-id"
                                     :time "call-time"})
                   (assoc :action-ongoing {:action {:action :ongoing}
                                           :start-time 42}))]
      (is (= {:producer "IVR"
              :subject "ACTION"
              :accountid "account-id"
              :applicationid "application-id"
              :callid "call-id"
              :callTime "call-time"
              :scriptid "script-id"
              :from "from-sda"
              :to "to-sda"
              :action {:action :ongoing}
              :time 71
              :duration 29}
             (call/call->action-ticket call 71)))))


  (testing "emit-state-ticket"
    (let [call (-> (call/info->call {:id "call-id" :time 42})
                   (assoc-in [:state :info] {:failed-sda "failed-sda"
                                             :overflow-cause "NO_AGENT"
                                             :queue "queue-id"
                                             :sda "sda"})
                   (assoc-in [:state :dial-status] {"dialstatus" "in-progress"
                                                    "dialcause" "hangup-a"
                                                    "bridgecause" "bridge-cause"
                                                    "bridgeduration" "bridge-duration"}))
          update {:now 71 :status {"cause" "xml-hangup"}}]

      (testing "Created -> AcdTransferred"
        (is (= {:ivr.ticket/emit
                {:state "Created"
                 :nextState "AcdTransferred"
                 :time 71
                 :duration 29
                 :queueid "queue-id"}}
               (call/emit-state-ticket
                 call
                 (assoc update :next-state "AcdTransferred")))))

      (testing "Created -> Created"
        (is (= nil
               (call/emit-state-ticket
                 call
                 (assoc update :next-state "Created")))))

      (testing "Created -> InProgress"
        (is (= {:ivr.ticket/emit
                {:state "Created"
                 :nextState "InProgress"
                 :time 71
                 :duration 29}}
               (call/emit-state-ticket
                 call
                 (assoc update :next-state "InProgress")))))

      (testing "Created -> Terminated"
        (is (= nil
               (call/emit-state-ticket
                 call
                 (assoc update :next-state "Terminated")))))

      (testing "Created -> Transferred"
        (is (= nil
               (call/emit-state-ticket
                 call
                 (assoc update :next-state "Transferred")))))

      (testing "Created -> TransferRinging"
        (is (= {:ivr.ticket/emit
                {:state "Created"
                 :nextState "TransferRinging"
                 :time 71
                 :duration 29
                 :ringingSda "sda"}}
               (call/emit-state-ticket
                 call
                 (assoc update :next-state "TransferRinging")))))

      (let [call (-> call
                     (assoc-in [:state :current] "AcdTransferred"))]

        (testing "AcdTransferred -> AcdTransferred"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "AcdTransferred -> Created"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "AcdTransferred -> InProgress"
          (is (= {:ivr.ticket/emit
                  {:state "AcdTransferred"
                   :nextState "InProgress"
                   :time 71
                   :duration 29
                   :acdcause "ACD_OVERFLOW"
                   :overflowcause "NO_AGENT"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "AcdTransferred -> Terminated"
          (is (= {:ivr.ticket/emit
                  {:state "AcdTransferred"
                   :nextState "Terminated"
                   :time 71
                   :duration 29
                   :acdcause "ACD_OVERFLOW"
                   :overflowcause "NO_AGENT"
                   :cause "IVR_HANG_UP"
                   :ccapi_cause "xml-hangup"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Terminated"))))
          (is (= {:ivr.ticket/emit
                  {:state "AcdTransferred"
                   :nextState "Terminated"
                   :time 71
                   :duration 29}}
                 (call/emit-state-ticket
                   call
                   (assoc update
                          :next-state "Terminated"
                          :status {"cause" "user-hangup"}))))
          (is (= {:ivr.ticket/emit
                  {:state "AcdTransferred"
                   :nextState "Terminated"
                   :time 71
                   :duration 29}}
                 (call/emit-state-ticket
                   (update-in call [:state :info] dissoc :overflow-cause)
                   (assoc update :next-state "Terminated")))))

        (testing "AcdTransferred -> Transferred"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "AcdTransferred -> TransferRinging"
          (is (= {:ivr.ticket/emit
                  {:state "AcdTransferred"
                   :nextState "TransferRinging"
                   :time 71
                   :duration 29
                   :acdcause "ACD_OVERFLOW"
                   :overflowcause "NO_AGENT"
                   :ringingSda "sda"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))

      (let [call (-> call
                     (assoc-in [:state :current] "InProgress"))]

        (testing "InProgress -> AcdTransferred"
          (is (= {:ivr.ticket/emit
                  {:state "InProgress"
                   :nextState "AcdTransferred"
                   :time 71
                   :duration 29
                   :queueid "queue-id"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "InProgress -> Created"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "InProgress -> InProgress"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "InProgress -> Terminated"
          (is (= {:ivr.ticket/emit
                  {:state "InProgress"
                   :nextState "Terminated"
                   :time 71
                   :duration 29
                   :cause "IVR_HANG_UP"
                   :ccapi_cause "xml-hangup"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Terminated"))))
          (is (= {:ivr.ticket/emit
                  {:state "InProgress"
                   :nextState "Terminated"
                   :time 71
                   :duration 29
                   :cause "CALLER_HANG_UP"}}
                 (call/emit-state-ticket
                   call
                   (assoc update
                          :next-state "Terminated"
                          :status {"cause" "user-hangup"})))))

        (testing "InProgress -> Transferred"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "InProgress -> TransferRinging"
          (is (= {:ivr.ticket/emit
                  {:state "InProgress"
                   :nextState "TransferRinging"
                   :time 71
                   :duration 29
                   :ringingSda "sda"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))

      (let [call (-> call
                     (assoc-in [:state :current] "Terminated"))]

        (testing "Terminated -> AcdTransferred"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "Terminated -> Created"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "Terminated -> InProgress"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "Terminated -> Terminated"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Terminated")))))

        (testing "Terminated -> Transferred"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "Terminated -> TransferRinging"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))

      (let [call (-> call
                     (assoc-in [:state :current] "Transferred"))]

        (testing "Transferred -> AcdTransferred"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "Transferred -> Created"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "Transferred -> InProgress"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "Transferred -> Terminated"
          (is (= {:ivr.ticket/emit
                  {:state "Transferred"
                   :nextState "Terminated"
                   :time 71
                   :duration 29
                   :sda "sda"
                   :bridgecause "bridge-cause"
                   :bridgeduration "bridge-duration"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Terminated")))))

        (testing "Transferred -> Transferred"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "Transferred -> TransferRinging"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))

      (let [call (-> call
                     (assoc-in [:state :current] "TransferRinging"))]

        (testing "TransferRinging -> AcdTransferred"
          (is (= {:ivr.ticket/emit
                  {:state "TransferRinging"
                   :nextState "AcdTransferred"
                   :time 71
                   :duration 29
                   :failedSda "sda"
                   :dialcause "in-progress"
                   :ccapi_dialcause "hangup-a"
                   :queueid "queue-id"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "TransferRinging -> Created"
          (is (= nil
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "TransferRinging -> InProgress"
          (is (= {:ivr.ticket/emit
                  {:state "TransferRinging"
                   :nextState "InProgress"
                   :time 71
                   :duration 29
                   :failedSda "sda"
                   :dialcause "in-progress"
                   :ccapi_dialcause "hangup-a"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "TransferRinging -> Terminated"
          (is (= {:ivr.ticket/emit
                  {:state "TransferRinging"
                   :nextState "Terminated"
                   :time 71
                   :duration 29}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Terminated"))))
          (is (= {:ivr.ticket/emit
                  {:state "TransferRinging"
                   :nextState "Terminated"
                   :time 71
                   :duration 29
                   :ringingSda "sda"
                   :cause "CALLER_HANG_UP"}}
                 (call/emit-state-ticket
                   call
                   (assoc update
                          :next-state "Terminated"
                          :status {"cause" "user-hangup"}))))
          (let [expected-ticket {:state "TransferRinging"
                                 :nextState "Terminated"
                                 :time 71
                                 :duration 29
                                 :cause "IVR_HANG_UP"
                                 :failedSda "sda"
                                 :ccapi_dialcause "hangup-a"
                                 :ccapi_cause "user-hangup"}
                update (assoc update
                              :next-state "Terminated"
                              :status {"cause" "user-hangup"})]
            (is (= {:ivr.ticket/emit
                    (assoc expected-ticket :dialcause "busy")}
                   (call/emit-state-ticket
                     (assoc-in call [:state :dial-status "dialstatus"] "busy")
                     update)))
            (is (= {:ivr.ticket/emit
                    (assoc expected-ticket :dialcause "failed")}
                   (call/emit-state-ticket
                     (assoc-in call [:state :dial-status "dialstatus"] "failed")
                     update)))
            (is (= {:ivr.ticket/emit
                    (assoc expected-ticket :dialcause "no-answer")}
                   (call/emit-state-ticket
                     (assoc-in call [:state :dial-status "dialstatus"] "no-answer")
                     update)))))

        (testing "TransferRinging -> Transferred"
          (is (= {:ivr.ticket/emit
                  {:state "TransferRinging"
                   :nextState "Transferred"
                   :time 71
                   :duration 29
                   :sda "sda"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "TransferRinging -> TransferRinging"
          (is (= {:ivr.ticket/emit
                  {:state "TransferRinging"
                   :nextState "TransferRinging"
                   :time 71
                   :duration 29
                   :failedSda "failed-sda"
                   :dialcause "in-progress"
                   :ccapi_dialcause "hangup-a"
                   :ringingSda "sda"}}
                 (call/emit-state-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))))


  (testing "change-state-event"
    (let [call (-> (call/info->call {:id "call-id" :time "call-time"})
                   (assoc-in [:state :current] "from-state"))
          update {:next-state "to-state" :now "now"}]
      (is (= {:dispatch-n
              [[:ivr.call/leave-state
                {:id "call-id" :time "now"
                 :from "from-state" :to "to-state"}]
               [:ivr.call/enter-state
                {:id "call-id" :time "now"
                 :from "from-state" :to "to-state"}]]}
             (call/change-state-event call update)))))


  (testing "db-insert-call"
    (let [call (call/info->call {:id "call-id" :time "call-time"})]
      (is (= {:calls {"call-id" {:info {:id "call-id"
                                        :time "call-time"}
                                 :state {:current "Created"
                                         :start-time "call-time"}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-insert-call {} call)))
      (is (= {:calls {"other-id" {:call :data}
                      "call-id" {:info {:id "call-id"
                                        :time "call-time"}
                                 :state {:current "Created"
                                         :start-time "call-time"}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-insert-call {:calls {"other-id" {:call :data}}} call)))))


  (testing "db-update-call"
    (let [call (call/info->call {:id "call-id" :time "call-time"})
          db (call/db-insert-call {} call)]
      (is (= {:calls {"call-id" {:info {:id "call-id"
                                        :time "call-time"}
                                 :state {:current "Created"
                                         :start-time "call-time"}
                                 :action-data {:action :data}
                                 :action-ongoing nil}}}
             (call/db-update-call db "call-id" assoc :action-data {:action :data})))
      (is (= {:calls {"call-id" {:info {:id "call-id"
                                        :time "call-time"
                                        :info :data}
                                 :state {:current "Created"
                                         :start-time "call-time"}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-update-call db "call-id" update :info merge {:info :data})))))


  (testing "db-call"
    (let [call (call/info->call {:id "call-id" :time "call-time"})
          db (call/db-insert-call {} call)]
      (is (= call
             (call/db-call db "call-id"))))))
