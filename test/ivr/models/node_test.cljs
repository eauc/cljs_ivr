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
                              :script-id "script-id"})))))
    ;; (testing "enter"
    ;;   (testing "unknown type"
    ;;     (is (= {:ivr.routes/response {:status 500
    ;;                                   :data {:status 500
    ;;                                          :status_code "invalid_node"
    ;;                                          :message "Invalid node - type"
    ;;                                          :cause {:type "unknown"}}}}
    ;;            (node/enter-type {:type "unknown"} options)))))
    ))
