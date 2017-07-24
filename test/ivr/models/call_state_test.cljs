(ns ivr.models.call-state-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test.alpha :as stest]
            [ivr.models.call :as call]
            [ivr.models.call-state :as call-state]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.call-state))
   :after (fn [] (stest/unstrument 'ivr.models.call-state))})

(deftest call-model-test

  (testing "on-enter"

    (testing "Transferred, increment sda limit"
      (let [call (-> (call/info->call {:id "call-id" :time 42})
                     (assoc-in [:state :info :sda] "sda"))
            event {:id (call/id call) :time 71
                   :from "FromState" :to "Transferred"
                   :cloudmemory #(assoc % :cloudmemory :query)
                   :services #(assoc % :services :query)}]
        (is (= {:ivr.web/request
                {:type :ivr.cloudmemory/inc-sda-limit
                 :sda "sda"
                 :cloudmemory :query}}
               (call-state/on-enter call event)))))


    (testing "Terminated, terminate call"
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
                     :to "Terminated"
                     :services #(assoc % :services :query)
                     :time 71}]

        (testing "from Created"
          (let [options (assoc options :from "Created")]
            (is (= {:ivr.call/remove "call-id"}
                   (call-state/on-enter call options)))))

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
            (is (= nil
                   (get (call-state/on-enter
                          (dissoc call :action-ongoing)
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "")
                   (get (call-state/on-enter
                          (assoc-in call [:state :info :overflow-cause] "overflow-cause")
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "")
                   (get (call-state/on-enter
                          (assoc-in call [:state :status "cause"] "xml-hangup")
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                   (get (call-state/on-enter
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
            (is (= nil
                   (get (call-state/on-enter
                          (dissoc call :action-ongoing)
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "CALLER_HANG_UP")
                   (get (call-state/on-enter
                          (assoc-in call [:state :status "cause"] "user-hangup")
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                   (get (call-state/on-enter
                          (assoc-in call [:state :status "cause"] "xml-hangup")
                          options)
                        :ivr.ticket/emit)))))

        (testing "from Transferred"
          (is (= {:ivr.call/remove "call-id"
                  :ivr.web/request {:services :query
                                    :type :ivr.services/call-on-end
                                    :action :data}}
                 (call-state/on-enter
                   (dissoc call :action-ongoing)
                   options)))
          (is (= {:ivr.call/remove "call-id"
                  :ivr.ticket/emit {:producer "IVR"
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
                                    :to "to"}
                  :ivr.web/request {:services :query
                                    :type :ivr.services/call-on-end
                                    :action :data}}
                 (call-state/on-enter call options))))

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
                   (get (call-state/on-enter
                          (dissoc call :action-ongoing)
                          options)
                        :ivr.ticket/emit)))
            (is (= expected-ticket
                   (get (call-state/on-enter
                          (assoc-in call [:state :status "cause"] "xml-hangup")
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "CALLER_HANG_UP")
                   (get (call-state/on-enter
                          call
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                   (get (call-state/on-enter
                          (assoc-in call [:state :dial-status "dialstatus"] "failed")
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                   (get (call-state/on-enter
                          (assoc-in call [:state :dial-status "dialstatus"] "no-answer")
                          options)
                        :ivr.ticket/emit)))
            (is (= (assoc expected-ticket :endCause "IVR_HANG_UP")
                   (get (call-state/on-enter
                          (assoc-in call [:state :dial-status "dialstatus"] "busy")
                          options)
                        :ivr.ticket/emit))))))))

  (testing "on leave"
    (let [call (-> (call/info->call {:id "call-id"
                                     :account-id "account-id"
                                     :time 42})
                   (assoc-in [:state :info :sda] "sda")
                   (assoc-in [:state :status "cause"] "user-hangup"))
          event {:id (call/id call) :time 71
                 :from "FromState" :to "ToState"
                 :cloudmemory #(assoc % :cloudmemory :query)
                 :acd #(assoc % :acd :query)}]


      (testing "Transferred, decrement sda limit"
        (let [event (assoc event :from "Transferred")]
          (is (= {:ivr.web/request
                  {:type :ivr.cloudmemory/dec-sda-limit
                   :sda "sda"
                   :cloudmemory :query}}
                 (call-state/on-leave call event)))))


      (testing "AcdTransferred, update acd status"
        (let [event (assoc event :from "AcdTransferred")]
          (is (= {:ivr.web/request
                  {:type :ivr.acd/update-call-status
                   :account-id "account-id"
                   :call-id "call-id"
                   :status "in-progress"
                   :cause "user-hangup"
                   :IVRStatus {:state "ToState"
                               :lastChange 71}
                   :acd :query}}
                 (call-state/on-leave call event)))))))


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
             (call-state/change-event call update)))))


  (testing "emit-state-ticket"
    (let [call (-> (call/info->call {:id "call-id"
                                     :time 42
                                     :application-id "app-id"
                                     :account-id "account-id"
                                     :script-id "script-id"
                                     :from "from"
                                     :to "to"})
                   (assoc-in [:state :info] {:failed-sda "failed-sda"
                                             :overflow-cause "NO_AGENT"
                                             :queue "queue-id"
                                             :sda "sda"})
                   (assoc-in [:state :dial-status] {"dialstatus" "in-progress"
                                                    "dialcause" "hangup-a"
                                                    "bridgecause" "bridge-cause"
                                                    "bridgeduration" "bridge-duration"}))
          update {:now 71 :status {"cause" "xml-hangup"}}
          base-ticket {:subject "CALL"
                       :applicationid "app-id"
                       :from "from"
                       :callTime 42
                       :callid "call-id"
                       :accountid "account-id"
                       :scriptid "script-id"
                       :to "to"
                       :time 71
                       :duration 29}]

      (testing "Created -> AcdTransferred"
        (is (= {:ivr.ticket/emit
                (merge base-ticket
                       {:state "Created"
                        :nextState "AcdTransferred"
                        :time 42
                        :duration 0
                        :queueid "queue-id"})}
               (call-state/emit-ticket
                 call
                 (assoc update :next-state "AcdTransferred")))))

      (testing "Created -> Created"
        (is (= nil
               (call-state/emit-ticket
                 call
                 (assoc update :next-state "Created")))))

      (testing "Created -> InProgress"
        (is (= {:ivr.ticket/emit
                (merge base-ticket
                       {:state "Created"
                        :nextState "InProgress"
                        :time 42
                        :duration 0})}
               (call-state/emit-ticket
                 call
                 (assoc update :next-state "InProgress")))))

      (testing "Created -> Terminated"
        (is (= nil
               (call-state/emit-ticket
                 call
                 (assoc update :next-state "Terminated")))))

      (testing "Created -> Transferred"
        (is (= nil
               (call-state/emit-ticket
                 call
                 (assoc update :next-state "Transferred")))))

      (testing "Created -> TransferRinging"
        (is (= {:ivr.ticket/emit
                (merge base-ticket
                       {:state "Created"
                        :nextState "TransferRinging"
                        :time 42
                        :duration 0
                        :ringingSda "sda"})}
               (call-state/emit-ticket
                 call
                 (assoc update :next-state "TransferRinging")))))

      (let [call (-> call
                     (assoc-in [:state :current] "AcdTransferred"))]

        (testing "AcdTransferred -> AcdTransferred"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "AcdTransferred -> Created"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "AcdTransferred -> InProgress"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "AcdTransferred"
                          :nextState "InProgress"
                          :acdcause "ACD_OVERFLOW"
                          :overflowcause "NO_AGENT"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "AcdTransferred -> Terminated"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "AcdTransferred"
                          :nextState "Terminated"
                          :acdcause "ACD_OVERFLOW"
                          :overflowcause "NO_AGENT"
                          :cause "IVR_HANG_UP"
                          :ccapi_cause "xml-hangup"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Terminated"))))
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "AcdTransferred"
                          :nextState "Terminated"})}
                 (call-state/emit-ticket
                   call
                   (assoc update
                          :next-state "Terminated"
                          :status {"cause" "user-hangup"}))))
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "AcdTransferred"
                          :nextState "Terminated"})}
                 (call-state/emit-ticket
                   (update-in call [:state :info] dissoc :overflow-cause)
                   (assoc update :next-state "Terminated")))))

        (testing "AcdTransferred -> Transferred"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "AcdTransferred -> TransferRinging"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "AcdTransferred"
                          :nextState "TransferRinging"
                          :acdcause "ACD_OVERFLOW"
                          :overflowcause "NO_AGENT"
                          :ringingSda "sda"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))

      (let [call (-> call
                     (assoc-in [:state :current] "InProgress"))]

        (testing "InProgress -> AcdTransferred"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "InProgress"
                          :nextState "AcdTransferred"
                          :queueid "queue-id"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "InProgress -> Created"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "InProgress -> InProgress"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "InProgress -> Terminated"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "InProgress"
                          :nextState "Terminated"
                          :cause "IVR_HANG_UP"
                          :ccapi_cause "xml-hangup"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Terminated"))))
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "InProgress"
                          :nextState "Terminated"
                          :cause "CALLER_HANG_UP"})}
                 (call-state/emit-ticket
                   call
                   (assoc update
                          :next-state "Terminated"
                          :status {"cause" "user-hangup"})))))

        (testing "InProgress -> Transferred"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "InProgress -> TransferRinging"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "InProgress"
                          :nextState "TransferRinging"
                          :ringingSda "sda"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))

      (let [call (-> call
                     (assoc-in [:state :current] "Terminated"))]

        (testing "Terminated -> AcdTransferred"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "Terminated -> Created"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "Terminated -> InProgress"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "Terminated -> Terminated"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Terminated")))))

        (testing "Terminated -> Transferred"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "Terminated -> TransferRinging"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))

      (let [call (-> call
                     (assoc-in [:state :current] "Transferred"))]

        (testing "Transferred -> AcdTransferred"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "Transferred -> Created"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "Transferred -> InProgress"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "Transferred -> Terminated"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "Transferred"
                          :nextState "Terminated"
                          :sda "sda"
                          :bridgecause "bridge-cause"
                          :bridgeduration "bridge-duration"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Terminated")))))

        (testing "Transferred -> Transferred"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "Transferred -> TransferRinging"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "TransferRinging"))))))

      (let [call (-> call
                     (assoc-in [:state :current] "TransferRinging"))]

        (testing "TransferRinging -> AcdTransferred"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "TransferRinging"
                          :nextState "AcdTransferred"
                          :failedSda "sda"
                          :dialcause "in-progress"
                          :ccapi_dialcause "hangup-a"
                          :queueid "queue-id"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "AcdTransferred")))))

        (testing "TransferRinging -> Created"
          (is (= nil
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Created")))))

        (testing "TransferRinging -> InProgress"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "TransferRinging"
                          :nextState "InProgress"
                          :failedSda "sda"
                          :dialcause "in-progress"
                          :ccapi_dialcause "hangup-a"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "InProgress")))))

        (testing "TransferRinging -> Terminated"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "TransferRinging"
                          :nextState "Terminated"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Terminated"))))
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "TransferRinging"
                          :nextState "Terminated"
                          :ringingSda "sda"
                          :cause "CALLER_HANG_UP"})}
                 (call-state/emit-ticket
                   call
                   (assoc update
                          :next-state "Terminated"
                          :status {"cause" "user-hangup"}))))
          (let [expected-ticket (merge base-ticket
                                       {:state "TransferRinging"
                                        :nextState "Terminated"
                                        :cause "IVR_HANG_UP"
                                        :failedSda "sda"
                                        :ccapi_dialcause "hangup-a"
                                        :ccapi_cause "user-hangup"})
                update (assoc update
                              :next-state "Terminated"
                              :status {"cause" "user-hangup"})]
            (is (= {:ivr.ticket/emit
                    (assoc expected-ticket :dialcause "busy")}
                   (call-state/emit-ticket
                     (assoc-in call [:state :dial-status "dialstatus"] "busy")
                     update)))
            (is (= {:ivr.ticket/emit
                    (assoc expected-ticket :dialcause "failed")}
                   (call-state/emit-ticket
                     (assoc-in call [:state :dial-status "dialstatus"] "failed")
                     update)))
            (is (= {:ivr.ticket/emit
                    (assoc expected-ticket :dialcause "no-answer")}
                   (call-state/emit-ticket
                     (assoc-in call [:state :dial-status "dialstatus"] "no-answer")
                     update)))))

        (testing "TransferRinging -> Transferred"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "TransferRinging"
                          :nextState "Transferred"
                          :sda "sda"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "Transferred")))))

        (testing "TransferRinging -> TransferRinging"
          (is (= {:ivr.ticket/emit
                  (merge base-ticket
                         {:state "TransferRinging"
                          :nextState "TransferRinging"
                          :failedSda "failed-sda"
                          :dialcause "in-progress"
                          :ccapi_dialcause "hangup-a"
                          :ringingSda "sda"})}
                 (call-state/emit-ticket
                   call
                   (assoc update :next-state "TransferRinging")))))))))
