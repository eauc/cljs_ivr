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
      (is (= {}
             (:action-data (call/info->call {:id "call-id" :from "invalid-sda"}))))
      (is (= {}
             (:action-data (call/info->call {:id "call-id" :from "047859abcd"})))))

    (testing "mobile number"
      (is (= {"zoneTel" 30}
             (:action-data (call/info->call {:id "call-id" :from "0666201317"}))))

      (testing "with paccess info"

        (testing "valid header with ZIP info"
          (is (= {"zoneTel" 3 "reg" "27" "dep" "71"}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; CP-71600; titi-cndwq"}))))
          (is (= {"zoneTel" 3 "reg" "27" "dep" "71"}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; CP-71600; titi"}))))
          (is (= {"zoneTel" 3 "reg" "27" "dep" "71"}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; CP-71600; "}))))
          (is (= {"zoneTel" 3 "reg" "27" "dep" "71"}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "CP-71600"})))))

        (testing "valid header without ZIP info"
          (is (= {"zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; titi-cndwq"})))))

        (testing "invalid header"
          (is (= {"zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu;CP-71600;titi"}))))
          (is (= {"zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header "toto-utu; CP71600; titi"}))))
          (is (= {"zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header ""}))))
          (is (= {"zoneTel" 30}
                 (:action-data (call/info->call {:id "call-id" :from "0666201317"
                                                 :paccess-header nil})))))))

    (testing "local number"
      (is (= {"dep" "93", "reg" "11", "zoneTel" 1}
             (:action-data (call/info->call {:id "call-id" :from "0178456732"}))))
      (is (= {"dep" "50", "reg" "28", "zoneTel" 2}
             (:action-data (call/info->call {:id "call-id" :from "0233456789"}))))
      (is (= {"dep" "71", "reg" "27", "zoneTel" 3}
             (:action-data (call/info->call {:id "call-id" :from "0385813045"}))))
      (is (= {"dep" "69", "reg" "84", "zoneTel" 4}
             (:action-data (call/info->call {:id "call-id" :from "0478597106"}))))
      (is (= {"dep" "17", "reg" "75", "zoneTel" 5}
             (:action-data (call/info->call {:id "call-id" :from "0546732819"})))))

    (testing "international number"
      (is (= {"dep" "69", "reg" "84", "zoneTel" 4}
             (:action-data (call/info->call {:id "call-id" :from "+33478597106"})))))))
