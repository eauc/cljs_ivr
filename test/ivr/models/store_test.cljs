(ns ivr.models.store-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.store :as store]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.store))
   :after (fn [] (stest/unstrument 'ivr.models.store))})

(deftest store-model
  (testing "query"
    (testing "get-script"
      (let [query {:type :ivr.store/get-script
                   :account-id "account-id"
                   :script-id "script-id"
                   :on-success [:success]
                   :on-error [:error]}]
        (is (= {:method "GET"
                :url "/cloudstore/account/account-id/script/script-id"
                :on-success [:success]
                :on-error [:error]}
               (store/query query)))))
    (testing "get-sound-by-name"
      (let [query {:type :ivr.store/get-sound-by-name
                   :name "sound"
                   :account-id "account-id"
                   :script-id "script-id"}]
        (is (= {:method "GET"
                :url "/cloudstore/account/account-id/file"
                :query {:query {:filename "sound"
                                :metadata.type "scriptSound"
                                :metadata.script "script-id"}}
                :on-success
                [:ivr.models.store/get-sound-success {:query query}]
                :on-error
                [:ivr.models.store/get-file-error
                 {:query (assoc query :url "/cloudstore/account/account-id/file")}]}
               (store/query query)))))
    (testing "get-sound-success"
      (let [query {:account-id "account-id"
                   :script-id "script-id"
                   :name "sound"
                   :on-success [:success {:payload "data"}]}]
        (testing "no result"
          (let [response (clj->js {:body {:meta {:total_count 0}}
                                   :objects []})]
            (is (= {:ivr.routes/response
                    {:status 500
                     :data {:status 500
                            :statusCode "sound_not_found"
                            :message "Sound not found"
                            :cause {:account-id "account-id"
                                    :script-id "script-id"
                                    :name "sound"}}}}
                   (store/get-sound-success query response)))))
        (testing "ok"
          (let [response (clj->js {:body {:meta {:total_count 2}
                                          :objects [{:_id "42"}
                                                    {:_id "54"}]}})]
            (is (= {:dispatch [:success {:payload "data"
                                         :sound-url "/cloudstore/file/42"}]}
                   (store/get-sound-success query response)))))))
    (testing "get-file-error"
      (is (= {:ivr.routes/response
              {:status 404
               :data {:status 404
                      :status_code "file_not_found"
                      :message "File not found"
                      :cause {:query "query"
                              :message "error message"}}}}
             (store/get-file-error {:query "query"}
                                   {:message "error message"}))))))
