(ns ivr.services.calls.resolve-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test.alpha :as stest]
            [ivr.services.calls.resolve :as resolve]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.services.calls.resolve))
   :after (fn [] (stest/unstrument 'ivr.services.calls.resolve))})

(deftest calls-resolve-service
  (testing "find-or-create-call"
    (let [db {:calls {"call1" {:id "call1"}}}
          coeffects {:db db :call-time-now "call-time-now"}
          params {"account_id" "account-id"
                  "application_id" "application-id"
                  "call_id" "new-call"
                  "from" "from-sda"
                  "to" "to-sda"
                  "script_id" "script-id"}]


      (testing "call does not exists, no create?, fail?"
        (is (= {:ivr.routes/response
                {:status 404
                 :data {:status 404
                        :status_code "call_not_found"
                        :message "Call not found"
                        :cause {:call-id "new-call"}}}}
               (resolve/find-or-create-call
                 coeffects
                 {:create? false :fail? true}
                 {:params params}))))

      (testing "call does not exists, no create?, no fail?"
        (is (= {:ivr.routes/response
                {:status 204}}
               (resolve/find-or-create-call
                 coeffects
                 {:create? false :fail? false}
                 {:params params}))))


      (testing "call does not exists, missing params for creation"
        (is (= {:ivr.routes/response
                {:status 400
                 :data {:status 400
                        :status_code "missing_request_params"
                        :message "Missing request params"
                        :cause {:call-id "new-call"
                                :account-id "missing"
                                :script-id "script-id"}}}}
               (resolve/find-or-create-call
                 coeffects {:create? true}
                 {:params (merge params {"account_id" nil})})))
        (is (= {:ivr.routes/response
                {:status 400
                 :data {:status 400
                        :status_code "missing_request_params"
                        :message "Missing request params"
                        :cause {:call-id "new-call"
                                :account-id "account-id"
                                :script-id "missing"}}}}
               (resolve/find-or-create-call
                 coeffects {:create? true}
                 {:params (merge params {"script_id" nil})})))
        (is (= {:ivr.routes/response
                {:status 400
                 :data {:status 400
                        :status_code "missing_request_params"
                        :message "Missing request params"
                        :cause {:call-id "missing"
                                :account-id "account-id"
                                :script-id "script-id"}}}}
               (resolve/find-or-create-call
                 coeffects {:create? true}
                 {:params (merge params {"call_id" nil})}))))


      (testing "call does not exits, create success"
        (let [expected-call {:info {:id "new-call"
                                    :account-id "account-id"
                                    :application-id "application-id"
                                    :from "from-sda"
                                    :to "to-sda"
                                    :script-id "script-id"
                                    :time "call-time-now"}
                             :state {:current "Created" :start-time "call-time-now"}
                             :action-data {"callid" "new-call"
                                           "accountid" "account-id"
                                           "applicationid" "application-id"
                                           "CALLER" "from-sda"
                                           "CALLEE" "to-sda"
                                           "scriptid" "script-id"
                                           "callTime" "call-time-now"}
                             :action-ongoing nil}]
          (is (= {:ivr.call/create expected-call
                  :ivr.routes/params (assoc params "call" expected-call)
                  :ivr.routes/next nil}
                 (resolve/find-or-create-call
                   coeffects {:create? true} {:params params})))))


      (testing "call exits, proceed"
        (is (= {:ivr.routes/params {"account_id" "account-id"
                                    "application_id" "application-id"
                                    "from" "from-sda"
                                    "to" "to-sda"
                                    "call_id" "call1"
                                    "script_id" "script-id"
                                    "call" {:id "call1"}}
                :ivr.routes/next nil}
               (resolve/find-or-create-call
                 coeffects {:create? true}
                 {:params (merge params {"call_id" "call1"})})))))))
