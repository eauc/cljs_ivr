(ns ivr.models.call-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.call :as call]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.call))
   :after (fn [] (stest/unstrument 'ivr.models.call))})

(deftest call-model-test

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


  (testing "db-insert-call"
    (let [call (call/info->call {:id "call-id"})]
      (is (= {:calls {"call-id" {:info {:id "call-id"}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-insert-call {} call)))
      (is (= {:calls {"other-id" {:call :data}
                      "call-id" {:info {:id "call-id"}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-insert-call {:calls {"other-id" {:call :data}}} call)))))


  (testing "db-update-call"
    (let [call (call/info->call {:id "call-id"})
          db (call/db-insert-call {} call)]
      (is (= {:calls {"call-id" {:info {:id "call-id"}
                                 :action-data {:action :data}
                                 :action-ongoing nil}}}
             (call/db-update-call db "call-id" assoc :action-data {:action :data})))
      (is (= {:calls {"call-id" {:info {:id "call-id"
                                        :info :data}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-update-call db "call-id" update :info merge {:info :data})))))


  (testing "db-call"
    (let [call (call/info->call {:id "call-id"})
          db (call/db-insert-call {} call)]
      (is (= call
             (call/db-call db "call-id"))))))
