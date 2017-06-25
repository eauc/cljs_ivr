(ns ivr.models.acd-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.acd :as acd]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.acd))
   :after (fn [] (stest/unstrument 'ivr.models.acd))})

(deftest acd-model-test

  (testing "unknown query"
    (is (= {:ivr.routes/response
            {:status 500
             :data {:status 500
                    :status_code "invalid_acd_query"
                    :message "Invalid ACD query - type"
                    :cause {:type :ivr.acd/unknown :params :values}}}}
           (acd/query {:type :ivr.acd/unknown :params :values}))))


  (testing "enqueue call"
    (let [request (acd/query {:type :ivr.acd/enqueue-call
                              :call {:info {:id "call-id" :time "call-time"}}
                              :account_id "account-id"
                              :application_id "app-id"
                              :node_id "node-id"
                              :queue_id "queue-id"
                              :script_id "script-id"
                              :to "to"
                              :from "from"
                              :on-success [:success]
                              :on-error [:error]})]
      (is (= {:method "POST"
              :url "/smartccacdlink/call/call-id/enqueue"
              :data {:call_id "call-id"
                     :callTime "call-time"
                     :account_id "account-id"
                     :application_id "app-id"
                     :queue_id "queue-id"
                     :to "to"
                     :from "from"
                     :ivr_fallback "/smartccivr/script/script-id/node/node-id/callback"}
              :on-error [:error]}
             (dissoc request :on-success)))


      (testing "enqueue call success"
        (let [on-success (:on-success request)]
          (is (= [:success {:wait-sound "wait-sound"}]
                 (on-success #js {:body {"waitSound" "wait-sound"}})))))))

  (testing "update call status"
    (let [query {:type :ivr.acd/update-call-status
                 :account-id "account-id"
                 :call-id "call-id"
                 :status "ringing"
                 :cause "hangup-a"
                 :IVRStatus {:state "TransferRinging"
                             :lastChange "change-time"}}
          request (acd/query query)]
      (is (= {:method "POST"
              :url "/smartccacdlink/call/call-id/principal/status"
              :data {:status "ringing"
                     :cause "hangup-a"
                     :IVRStatus {:state "TransferRinging"
                                 :lastChange "change-time"}
                     :account_id "account-id"
                     :call_id "call-id"}
              :on-success
              [:ivr.acd/update-call-status-success query],
              :on-error
              [:ivr.acd/update-call-status-error query]}
             request))

      (testing "on success"
        (is (= {} (acd/update-call-status-success {} query))))

      (testing "on error"
        (is (= {} (acd/update-call-status-error {} query)))))))
