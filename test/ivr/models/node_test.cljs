(ns ivr.models.node-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.node :as node]
            [ivr.models.script :as script]
            [ivr.models.node-set :as node-set]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node))
   :after (fn [] (stest/unstrument 'ivr.models.node))})

(deftest node-model
  (let [store (fn [query] {:store "mock"
                           :query query})
        verbs (fn [actions] {:verbs "mock"
                             :actions actions})
        options {:store store
                 :verbs verbs}]

    (testing "conform"
      (testing "unknown type"
        (is (= {"type" "unknown"
                "id" "node-id"
                "account_id" "account-id"
                "script_id" "script-id"}
               (node/conform {"type" "unknown"}
                             {:id "node-id"
                              :account-id "account-id"
                              :script-id "script-id"}))))

      (testing "preset"
        (is (= {"type" "announcement"
                "id" "node-id"
                "account_id" "account-id"
                "script_id" "script-id"
                "preset" [{:value "value"
                           :to "to_var"}]}
               (node/conform {"type" "announcement"
                              "preset" {"value" "value"
                                        "varname" "to_var"}}
                             {:id "node-id"
                              :account-id "account-id"
                              :script-id "script-id"})))
        (is (= {"type" "announcement"
                "id" "node-id"
                "account_id" "account-id"
                "script_id" "script-id"
                "preset" [{:from "from_var"
                           :to "to_var"}]}
               (node/conform {"type" "announcement"
                              "preset" {"value" "$from_var"
                                        "varname" "to_var"}}
                             {:id "node-id"
                              :account-id "account-id"
                              :script-id "script-id"})))))


    (testing "enter"


      (testing "unknown type"
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_node"
                        :message "Invalid node - type"
                        :cause {"type" "unknown"}}}}
               (node/enter-type {"type" "unknown"} options))))


      (testing "preset"
        (let [store (fn [query] {:store "mock"
                                 :query query})
              verbs (fn [actions] {:verbs "mock"
                                   :actions actions})
              deps {:store store
                    :verbs verbs}
              call {:action-data {"action" "data"}
                    :info {:id "call-id"}}
              options {:call call
                       :deps deps
                       :params {}}]
          (let [node {"type" "announcement"
                      "account_id" "account-id"
                      "script_id" "script-id"
                      "soundname" "sound"
                      "preset" [(node-set/map->SetEntry
                                  {:to "to_var"
                                   :value "set_value"})]}]
            (is (= {:id "call-id"
                    :action-data {"action" "data"
                                  "to_var" "set_value"}}
                   (:ivr.call/update
                    (node/enter-type node options)))))

          (let [node {"type" "announcement"
                      "account-id" "account-id"
                      "script-id" "script-id"
                      "soundname" "sound"
                      "preset" [(node-set/map->SetEntry
                                  {:to "action"
                                   :value "to_value"})
                                (node-set/map->CopyEntry
                                  {:to "to_var"
                                   :from "action"})]}]
            (is (= {:id "call-id"
                    :action-data {"action" "to_value"
                                  "to_var" "to_value"}}
                   (:ivr.call/update
                    (node/enter-type node options))))))))


    (testing "leave"


      (testing "unknown type"
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_node"
                        :message "Invalid node - type"
                        :cause {"type" "unknown"}}}}
               (node/leave-type {"type" "unknown"} options)))))))
