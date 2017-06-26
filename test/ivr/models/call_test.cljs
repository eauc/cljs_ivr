(ns ivr.models.call-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.call :as call]
            [ivr.models.call-state :as call-state]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.call))
   :after (fn [] (stest/unstrument 'ivr.models.call))})

(deftest call-model-test


  (testing "geo-localisation"

    (testing "invalid number"
      (is (= {}
             (:action-data (call/info->call {:id "call-id" :from "invalid-sda"}))))
      (is (= {}
             (:action-data (call/info->call {:id "call-id" :from "047859abcd"})))))

    (testing "mobile number"
      (is (= {"zoneTel" 30}
             (:action-data (call/info->call {:id "call-id" :from "0666201317"})))))

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
             (:action-data (call/info->call {:id "call-id" :from "+33478597106"}))))))


  (testing "update-sda"
    (let [call (call/info->call {:id "call-id" :time "call-time"})]
      (is (= [:ivr.call/state {:id "call-id", :info {:sda "new-sda"}}]
             (call/update-sda call "new-sda"))))
    (let [call (-> (call/info->call {:id "call-id" :time "call-time"})
                   (assoc-in [:state :info :sda] "old-sda"))]
      (is (= [:ivr.call/state {:id "call-id", :info {:sda "new-sda"}}]
             (call/update-sda call "new-sda"))))
    (let [call (-> (call/info->call {:id "call-id" :time "call-time"})
                   (assoc-in [:state :current] "TransferRinging")
                   (assoc-in [:state :info :sda] "old-sda"))]
      (is (= [:ivr.call/state {:id "call-id", :info {:sda "new-sda"
                                                     :failed-sda "old-sda"}}]
             (call/update-sda call "new-sda")))))


  (testing "db-insert-call"
    (let [call (call/info->call {:id "call-id" :time "call-time"})]
      (is (= {:calls {"call-id" {:info {:id "call-id"
                                        :time "call-time"}
                                 :state {:current "Created"
                                         :start-time "call-time"}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-insert-call {} call)))
      (is (= {:calls {"other-id" {:call :data}
                      "call-id" {:info {:id "call-id"
                                        :time "call-time"}
                                 :state {:current "Created"
                                         :start-time "call-time"}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-insert-call {:calls {"other-id" {:call :data}}} call)))))


  (testing "db-update-call"
    (let [call (call/info->call {:id "call-id" :time "call-time"})
          db (call/db-insert-call {} call)]
      (is (= {:calls {"call-id" {:info {:id "call-id"
                                        :time "call-time"}
                                 :state {:current "Created"
                                         :start-time "call-time"}
                                 :action-data {:action :data}
                                 :action-ongoing nil}}}
             (call/db-update-call db "call-id" assoc :action-data {:action :data})))
      (is (= {:calls {"call-id" {:info {:id "call-id"
                                        :time "call-time"
                                        :info :data}
                                 :state {:current "Created"
                                         :start-time "call-time"}
                                 :action-data {}
                                 :action-ongoing nil}}}
             (call/db-update-call db "call-id" update :info merge {:info :data})))))


  (testing "db-call"
    (let [call (call/info->call {:id "call-id" :time "call-time"})
          db (call/db-insert-call {} call)]
      (is (= call
             (call/db-call db "call-id"))))))
