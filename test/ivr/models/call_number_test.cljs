(ns ivr.models.call-number-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.call :as call]))


(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.call))
   :after (fn [] (stest/unstrument 'ivr.models.call))})


(deftest call-number-model-test

  (testing "geo-localisation"

    (testing "invalid number"
      (is (= {"callid" "call-id", "CALLER" "invalid-sda"}
             (:action-data (call/info->call {:id "call-id" :from "invalid-sda"}))))
      (is (= {"callid" "call-id", "CALLER" "047859abcd"}
             (:action-data (call/info->call {:id "call-id" :from "047859abcd"})))))

    (testing "mobile number"
      (is (= {"callid" "call-id", "CALLER" "0666201317"
              "zoneTel" 30}
             (:action-data (call/info->call {:id "call-id" :from "0666201317"}))))

      (testing "with paccess info"

        (testing "valid header with ZIP info"
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 3 "reg" "27" "dep" "71"}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; CP-71600; titi-cndwq"}))))
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 3 "reg" "27" "dep" "71"}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; CP-71600; titi"}))))
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 3 "reg" "27" "dep" "71"}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; CP-71600; "}))))
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 3 "reg" "27" "dep" "71"}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "CP-71600"})))))

        (testing "valid header without ZIP info"
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; titi-cndwq"})))))

        (testing "invalid header"
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu;CP-71600;titi"}))))
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; CP71600; titi"}))))
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header ""}))))
          (is (= {"callid" "call-id", "CALLER" "0666201317"
                  "zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header nil})))))))

    (testing "local number"
      (is (= {"callid" "call-id", "CALLER" "0178456732"
               "dep" "93", "reg" "11", "zoneTel" 1}
             (:action-data (call/info->call {:id "call-id" :from "0178456732"}))))
      (is (= {"callid" "call-id", "CALLER" "0233456789"
              "dep" "50", "reg" "28", "zoneTel" 2}
             (:action-data (call/info->call {:id "call-id" :from "0233456789"}))))
      (is (= {"callid" "call-id", "CALLER" "0385813045"
              "dep" "71", "reg" "27", "zoneTel" 3}
             (:action-data (call/info->call {:id "call-id" :from "0385813045"}))))
      (is (= {"callid" "call-id", "CALLER" "0478597106"
              "dep" "69", "reg" "84", "zoneTel" 4}
             (:action-data (call/info->call {:id "call-id" :from "0478597106"}))))
      (is (= {"callid" "call-id","CALLER" "0546732819"
              "dep" "17", "reg" "75", "zoneTel" 5}
             (:action-data (call/info->call {:id "call-id" :from "0546732819"})))))

    (testing "international number"
      (is (= {"callid" "call-id",
              "CALLER" "+33478597106",
              "dep" "69"
              "reg" "84"
              "zoneTel" 4}
             (:action-data (call/info->call {:id "call-id" :from "+33478597106"})))))))
