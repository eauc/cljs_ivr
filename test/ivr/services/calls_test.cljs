(ns ivr.services.calls-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec :as spec]
            [ivr.services.calls :as calls]))

(deftest calls-service
  (testing "find-or-create-call"
    (let [db {:calls {"call1" {:id "call1"}}}
          params {:account_id "account-id"
                  :call_id "new-call"
                  :script_id "script-id"}]
      (testing "call does not exists, no create?"
        (is (= {:ivr.routes/response
                {:status 404
                 :data {:status 404
                        :status_code "call_not_found"
                        :message "Call not found"
                        :cause {:call-id "new-call"}}}}
               (calls/find-or-create-call {:db db} {:create? false} {:params params}))))
      (testing "call does not exists, missing params for creation"
        (is (= {:ivr.routes/response
                {:status 400
                 :data {:status 400
                        :status_code "missing_request_params"
                        :message "Missing request params"
                        :cause {:call-id "new-call"
                                :account-id "missing"
                                :script-id "script-id"}}}}
               (calls/find-or-create-call {:db db} {:create? true}
                                          {:params (merge params {:account_id nil})})))
        (is (= {:ivr.routes/response
                {:status 400
                 :data {:status 400
                        :status_code "missing_request_params"
                        :message "Missing request params"
                        :cause {:call-id "new-call"
                                :account-id "account-id"
                                :script-id "missing"}}}}
               (calls/find-or-create-call {:db db} {:create? true}
                                          {:params (merge params {:script_id nil})})))
        (is (= {:ivr.routes/response
                {:status 400
                 :data {:status 400
                        :status_code "missing_request_params"
                        :message "Missing request params"
                        :cause {:call-id "missing"
                                :account-id "account-id"
                                :script-id "script-id"}}}}
               (calls/find-or-create-call {:db db} {:create? true}
                                          {:params (merge params {:call_id nil})}))))
      (testing "call does not exits, create success"
        (is (= {:db {:calls {"call1" {:id "call1"}
                             "new-call" {:info {:id "new-call"
                                                :account-id "account-id"
                                                :script-id "script-id"}
                                         :action-data {}}}}
                :ivr.routes/params {:account_id "account-id"
                                    :call_id "new-call"
                                    :script_id "script-id"
                                    :call {:info {:id "new-call"
                                                  :account-id "account-id"
                                                  :script-id "script-id"}
                                           :action-data {}}}
                :ivr.routes/next nil}
               (calls/find-or-create-call {:db db} {:create? true} {:params params}))))
      (testing "call exits, proceed"
        (is (= {:ivr.routes/params {:account_id "account-id"
                                    :call_id "call1"
                                    :script_id "script-id"
                                    :call {:id "call1"}}
                :ivr.routes/next nil}
               (calls/find-or-create-call {:db db} {:create? true}
                                          {:params (merge params {:call_id "call1"})})))))))
