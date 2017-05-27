(ns ivr.models.node-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.node :as node]))

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
        (is (= {:type "unknown"
                :id "node-id"
                :account-id "account-id"
                :script-id "script-id"
                :next nil}
               (node/conform {:type "unknown"}
                             {:id "node-id"
                              :account-id "account-id"
                              :script-id "script-id"}))))
      (testing "preset"
        (is (= {:type "announcement"
                :id "node-id"
                :account-id "account-id"
                :script-id "script-id"
                :next nil
                :preset {:type :ivr.node.preset/set
                         :value "value"
                         :to :to_var}}
               (node/conform {:type "announcement"
                              :preset {:value "value"
                                       :varname "to_var"}}
                             {:id "node-id"
                              :account-id "account-id"
                              :script-id "script-id"})))
        (is (= {:type "announcement"
                :id "node-id"
                :account-id "account-id"
                :script-id "script-id"
                :next nil
                :preset {:type :ivr.node.preset/copy
                         :from :from_var
                         :to :to_var}}
               (node/conform {:type "announcement"
                              :preset {:value "$from_var"
                                       :varname "to_var"}}
                             {:id "node-id"
                              :account-id "account-id"
                              :script-id "script-id"})))))
    (testing "enter"
      (testing "unknown type"
        (is (= {:ivr.routes/response {:status 500
                                      :data {:status 500
                                             :status_code "invalid_node"
                                             :message "Invalid node - type"
                                             :cause {:type "unknown"}}}}
               (node/enter-type {:type "unknown"} options))))
      (testing "preset"
        (let [store (fn [query] {:store "mock"
                                 :query query})
              verbs (fn [actions] {:verbs "mock"
                                   :actions actions})
              options {:action-data {:action "data"}
                       :call-id "call-id"
                       :store store
                       :verbs verbs}]
          (let [node {:type "announcement"
                      :account-id "account-id"
                      :script-id "script-id"
                      :soundname "sound"
                      :preset {:type :ivr.node.preset/set
                               :to :to_var
                               :value "set_value"}}]
            (is (= {:call-id "call-id"
                    :data {:action "data"
                           :to_var "set_value"}}
                   (:ivr.call/action-data
                    (node/enter-type node options)))))
          (let [node {:type "announcement"
                      :account-id "account-id"
                      :script-id "script-id"
                      :soundname "sound"
                      :preset {:type :ivr.node.preset/copy
                               :to :to_var
                               :from :action}}]
            (is (= {:call-id "call-id"
                    :data {:action "data"
                           :to_var "data"}}
                   (:ivr.call/action-data
                    (node/enter-type node options))))))))
    (testing "leave"
      (testing "unknown type"
        (is (= {:ivr.routes/response {:status 500
                                      :data {:status 500
                                             :status_code "invalid_node"
                                             :message "Invalid node - type"
                                             :cause {:type "unknown"}}}}
               (node/leave-type {:type "unknown"} options)))))))
