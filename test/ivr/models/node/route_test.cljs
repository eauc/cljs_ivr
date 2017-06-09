(ns ivr.models.node.route-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.node :as node]
            [ivr.models.node.route :as route-node]
            [ivr.models.node-set :as node-set]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node.route))
   :after (fn [] (stest/unstrument 'ivr.models.node.route))})

(deftest route-node-test
  (testing "conform"
    (is (= {"type" "route"}
           (node/conform-type {"type" "route"})))
    (is (= {"type" "route"
            "varname" "toto"}
           (node/conform-type {"type" "route"
                               "varname" "toto"})))
    (is (= {"type" "route"
            "varname" "toto"}
           (node/conform-type {"type" "route"
                               "varname" "toto"
                               "case" "case"})))
    (is (= {"type" "route"
            "varname" "toto"
            "case" {}}
           (node/conform-type {"type" "route"
                               "varname" "toto"
                               "case" {}})))
    (is (= {"type" "route"
            "varname" "toto"
            "case" {"value" {"next" "42"}}}
           (node/conform-type {"type" "route"
                               "varname" "toto"
                               "case" {"value" "42"}})))
    (is (= {"type" "route"
            "varname" "toto"
            "case" {"value1" {"next" "42"}
                    "value2" {"set" [{:value "titi" :to "toto"}]}
                    "value3" {"next" "56"
                              "set" [{:from "toto" :to "tutu"}]}}}
           (node/conform-type
             {"type" "route"
              "varname" "toto"
              "case" {"value1" {"next" "42"}
                      "value2" {"set" {"varname" "toto"
                                       "value" "titi"}}
                      "value3" {"next" "56"
                                "set" {"varname" "tutu"
                                       "value" "$toto"}}}}))))


  (testing "enter"
    (let [verbs (fn [vs] {:verbs :create :data vs})
          deps {:verbs verbs}
          call {:action-data {"action" "data"}
                :info {:id "call-id"}}
          context {:call call :deps deps}
          base-node {"type" "route"
                     "script_id" "script-id"
                     "varname" "toto"
                     "case" {"value1" {"next" "42"}
                             "value2" {"set" [(node-set/map->CopyEntry
                                                {:to "to_fetch"
                                                 :from "titi"})]}
                             "value3" {"set" [(node-set/map->SetEntry
                                                {:to "to_fetch"
                                                 :value "val3"})]}
                             "value4" {"next" "44"
                                       "set" [(node-set/map->SetEntry
                                                {:to "to_fetch"
                                                 :value "val4"})]}}}]


      (testing "varname is not set in action-data"
        (testing "case has no default"
          (is (= {:ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/hangup}]}}
                 (node/enter-type base-node context))))
        (testing "case has a default"
          (let [node (update base-node "case"
                             merge {"_other" {"next" "44"
                                              "set" [(node-set/map->SetEntry
                                                       {:to "to_fetch"
                                                        :value "val4"})]}})]
            (is (= {:ivr.call/action-data
                    {:info {:id "call-id"}
                     :action-data {"action" "data"
                                   "to_fetch" "val4"}}
                    :ivr.routes/response
                    {:verbs :create
                     :data [{:type :ivr.verbs/redirect
                             :path "/smartccivr/script/script-id/node/44"}]}}
                   (node/enter-type node context))))))


        (testing "case is a simple next"
          (is (= {:ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/redirect
                           :path "/smartccivr/script/script-id/node/42"}]}}
                 (node/enter-type
                   base-node
                   (assoc context :call (merge call {:action-data {"toto" "value1"}}))))))


        (testing "case is a simple set"
          (is (= {:ivr.call/action-data
                  {:info {:id "call-id"}
                   :action-data {"toto" "value2"
                                 "titi" "hello"
                                 "to_fetch" "hello"}}
                  :ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/hangup}]}}
                 (node/enter-type
                   base-node
                   (assoc context :call (merge call {:action-data {"toto" "value2"
                                                                   "titi" "hello"}})))))
          (is (= {:ivr.call/action-data
                  {:info {:id "call-id"}
                   :action-data {"toto" "value3"
                                 "to_fetch" "val3"}}
                  :ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/hangup}]}}
                 (node/enter-type
                   base-node
                   (assoc context :call (merge call {:action-data {"toto" "value3"}}))))))


        (testing "case is both a set & a next"
          (is (= {:ivr.call/action-data
                  {:info {:id "call-id"}
                   :action-data {"toto" "value4"
                                 "to_fetch" "val4"}}
                  :ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/redirect
                           :path "/smartccivr/script/script-id/node/44"}]}}
                 (node/enter-type
                   base-node
                   (assoc context :call (merge call {:action-data {"toto" "value4"}})))))))))
