(ns ivr.models.call-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.call :as call]))

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


  (testing "inc-sda-limit"
    (let [call (-> (call/info->call {:id "call-id" :time "call-time"})
                   (assoc-in [:state :info :sda] "ringing-sda"))
          deps {:cloudmemory #(assoc % :cloudmemory :query)}]
      (is (= {:ivr.web/request
              {:cloudmemory :query
               :type :ivr.cloudmemory/inc-sda-limit
               :sda "ringing-sda"}}
             (call/inc-sda-limit call deps)))))


  (testing "dec-sda-limit"
    (let [call (-> (call/info->call {:id "call-id" :time "call-time"})
                   (assoc-in [:state :info :sda] "ringing-sda"))
          deps {:cloudmemory #(assoc % :cloudmemory :query)}]
      (is (= {:ivr.web/request
              {:cloudmemory :query
               :type :ivr.cloudmemory/dec-sda-limit
               :sda "ringing-sda"}}
             (call/dec-sda-limit call deps)))))


  (testing "update-acd-status"
    (let [call (-> (call/info->call {:id "call-id" :time "call-time"})
                   (assoc-in [:info :account-id] "account-id")
                   (assoc-in [:state :status] {"cause" "xml-hangup"
                                               "status" "failed"}))
          options {:acd #(assoc % :acd :query)
                   :next-state "InProgress"
                   :time "now"}]
      (is (= {:ivr.web/request
              {:acd :query
               :type :ivr.acd/update-call-status
               :account-id "account-id"
               :call-id "call-id"
               :status "failed"
               :cause "xml-hangup"
               :IVRStatus {:state "InProgress" :lastChange "now"}}}
             (call/update-acd-status call options)))))


  (testing "terminate"
    (let [call (-> (call/info->call {:id "call-id"
                                     :account-id "account-id"
                                     :application-id "app-id"
                                     :script-id "script-id"
                                     :from "from"
                                     :to "to"
                                     :time 42})
                   (assoc :action-data {:action :data})
                   (assoc :action-ongoing {:action :ongoing
                                           :start-time 14}))
          options {:from "Transferred"
                   :services #(assoc % :services :query)
                   :time 71}]

      (testing "from AcdTransferred"
        (let [options (assoc options :from "AcdTransferred")
              expected-ticket {:producer "IVR"
                               :time 71
                               :duration 57
                               :applicationid "app-id"
                               :from "from"
                               :callTime 42
                               :callid "call-id"
                               :action :ongoing
                               :accountid "account-id"
                               :subject "ACTION"
                               :scriptid "script-id"
                               :to "to"}]
          (is (= (assoc expected-ticket :endCause "")
                 (get (call/terminate
                        (assoc-in call [:state :info :overflow-cause] "overflow-cause")
                        options)
                      :ivr.ticket/emit)))
          (is (= (assoc expected-ticket :endCause "")
                 (get (call/terminate
                        (assoc-in call [:state :status "cause"] "xml-hangup")
                        options)
                      :ivr.ticket/emit)))
          (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                 (get (call/terminate
                        (-> call
                            (assoc-in [:state :info :overflow-cause] "overflow-cause")
                            (assoc-in [:state :status "cause"] "xml-hangup"))
                        options)
                      :ivr.ticket/emit)))))

      (testing "from InProgress"
        (let [options (assoc options :from "InProgress")
              expected-ticket {:producer "IVR"
                               :time 71
                               :duration 57
                               :applicationid "app-id"
                               :from "from"
                               :callTime 42
                               :callid "call-id"
                               :action :ongoing
                               :accountid "account-id"
                               :subject "ACTION"
                               :scriptid "script-id"
                               :to "to"}]
          (is (= (assoc expected-ticket :endCause "CALLER_HANG_UP")
                 (get (call/terminate
                        (assoc-in call [:state :status "cause"] "user-hangup")
                        options)
                      :ivr.ticket/emit)))
          (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                 (get (call/terminate
                        (assoc-in call [:state :status "cause"] "xml-hangup")
                        options)
                      :ivr.ticket/emit)))))

      (testing "from Transferred"
        (is (= {:ivr.call/remove "call-id"
                :ivr.web/request {:services :query
                                  :type :ivr.services/call-on-end
                                  :action :data
                                  :accountid "account-id"
                                  :applicationid "app-id"
                                  :callid "call-id"
                                  :scriptid "script-id"
                                  :from "from"
                                  :to "to"
                                  :callTime 42}}
               (call/terminate call options))))

      (testing "from TransferRinging"
        (let [call (assoc-in call [:state :status "cause"] "user-hangup")
              options (assoc options :from "TransferRinging")
              expected-ticket {:producer "IVR"
                               :time 71
                               :duration 57
                               :applicationid "app-id"
                               :from "from"
                               :callTime 42
                               :callid "call-id"
                               :action :ongoing
                               :accountid "account-id"
                               :subject "ACTION"
                               :scriptid "script-id"
                               :to "to"}]
          (is (= nil
                 (get (call/terminate
                        (assoc-in call [:state :status "cause"] "xml-hangup")
                        options)
                      :ivr.ticket/emit)))
          (is (= (assoc expected-ticket :endCause "CALLER_HANG_UP")
                 (get (call/terminate
                        call
                        options)
                      :ivr.ticket/emit)))
          (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                 (get (call/terminate
                        (assoc-in call [:state :dial-status "dialstatus"] "failed")
                        options)
                      :ivr.ticket/emit)))
          (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                 (get (call/terminate
                        (assoc-in call [:state :dial-status "dialstatus"] "no-answer")
                        options)
                      :ivr.ticket/emit)))
          (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                 (get (call/terminate
                        (assoc-in call [:state :dial-status "dialstatus"] "busy")
                        options)
                      :ivr.ticket/emit)))))))


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
