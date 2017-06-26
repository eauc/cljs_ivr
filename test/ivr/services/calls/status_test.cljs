(ns ivr.services.calls.status-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [deftest is testing use-fixtures]]
            [ivr.models.call :as call]
            [ivr.services.calls.status :as status]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.services.calls.status))
   :after (fn [] (stest/unstrument 'ivr.services.calls.status))})


(deftest calls-status-services

  (testing "call status route"
    (let [call (call/info->call {:id "call-id"})
          params {"call" call
                  "status" "in-progress"
                  "cause" "user-hangup"
                  "other" "ignored"}
          route {:params params}]


      (testing "simple status update"
        (is (= {:ivr.routes/response {:status 204}
                :dispatch
                [:ivr.call/state {:id "call-id"
                                  :status {"status" "in-progress"
                                           "cause" "user-hangup"}}]}
               (status/call-status-route {} route))))

      (testing "call state -> Terminated"
        (let [params (assoc params "status" "canceled")
              route {:params params}]
          (is (= [:ivr.call/state {:id "call-id"
                                   :next-state "Terminated"
                                   :status {"status" "canceled"
                                            "cause" "user-hangup"}}]
                 (:dispatch
                  (status/call-status-route {} route)))))

        (let [params (assoc params "status" "completed")
              route {:params params}]
          (is (= [:ivr.call/state {:id "call-id"
                                   :next-state "Terminated"
                                   :status {"status" "completed"
                                            "cause" "user-hangup"}}]
                 (:dispatch
                  (status/call-status-route {} route)))))

        (let [params (assoc params "status" "failed")
              route {:params params}]
          (is (= [:ivr.call/state {:id "call-id"
                                   :next-state "Terminated"
                                   :status {"status" "failed"
                                            "cause" "user-hangup"}}]
                 (:dispatch
                  (status/call-status-route {} route))))))))

  (testing "dial status route"
    (let [call (call/info->call {:id "call-id"})
          params {"call" call
                  "bridgecause" "hangup-a"
                  "bridgeduration" "42.0"
                  "dialstatus" "completed"
                  "dialcause" "user-hangup"}
          route {:params params}]


      (testing "simple dial-status update"
        (is (= {:ivr.routes/response {:status 204},
                :dispatch
                [:ivr.call/state {:id "call-id"
                                  :dial-status {"bridgecause" "hangup-a"
                                                "bridgeduration" "42.0"
                                                "dialstatus" "completed"
                                                "dialcause" "user-hangup"}}]}
               (status/call-dial-status-route {} route))))


      (testing "call state TransferRinging -> Transferred"
        (let [call (assoc-in call [:state :current] "TransferRinging")
              params (merge params {"call" call "bridgestatus" "in-progress"})
              route {:params params}]
          (is (= [:ivr.call/state {:id "call-id"
                                   :next-state "Transferred"
                                   :dial-status {"bridgecause" "hangup-a"
                                                 "bridgeduration" "42.0"
                                                 "dialstatus" "completed"
                                                 "dialcause" "user-hangup"}}]
                 (:dispatch
                  (status/call-dial-status-route {} route)))))))))
