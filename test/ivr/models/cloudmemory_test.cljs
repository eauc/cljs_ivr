(ns ivr.models.cloudmemory-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.cloudmemory :as cloudmemory]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.cloudmemory))
   :after (fn [] (stest/unstrument 'ivr.models.cloudmemory))})

(deftest cloudmemory-model

  (testing "inc-sda-limit"
    (is (= {:method "GET"
            :url "/cloudmemory/counter/inc-sda/incr"
            :on-success
            [:ivr.cloudmemory/sda-limit-success
             {:sda "inc-sda", :action "inc"}]
            :on-error
            [:ivr.cloudmemory/sda-limit-error
             {:sda "inc-sda", :action "inc"}]}
           (cloudmemory/query {:type :ivr.cloudmemory/inc-sda-limit
                               :sda "inc-sda"}))))


  (testing "dec-sda-limit"
    (is (= {:method "GET"
            :url "/cloudmemory/counter/dec-sda/decr"
            :on-success
            [:ivr.cloudmemory/sda-limit-success
             {:sda "dec-sda", :action "dec"}]
            :on-error
            [:ivr.cloudmemory/sda-limit-error
             {:sda "dec-sda", :action "dec"}]}
           (cloudmemory/query {:type :ivr.cloudmemory/dec-sda-limit
                               :sda "dec-sda"}))))

  (testing "sda-limit-success"
    (is (= {}
           (cloudmemory/sda-limit-success {} {:sda "sda" :action "inc"}))))


  (testing "sda-limit-error"
    (is (= {}
           (cloudmemory/sda-limit-error {} {:sda "sda" :action "inc"})))))
