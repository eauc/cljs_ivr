(ns ivr.models.script-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.script :as script]))

(deftest script-model
  (let [options {:account-id "account-id"}]
    (testing "conform"
      (testing "start"
        (is (= {:account-id "account-id"
                :start nil
                :nodes {}}
               (script/conform {} options)))
        (is (= {:account-id "account-id"
                :start :42
                :nodes {}}
               (script/conform {:start "42"} options)))
        (is (= {:account-id "account-id"
                :start :42
                :nodes {}}
               (script/conform {:start 42} options)))
        (is (= {:account-id "account-id"
                :start :false
                :nodes {}}
               (script/conform {:start false} options))))
      (testing "nodes"
        (is (= {:account-id "account-id"
                :start nil
                :nodes {:1 nil}}
               (script/conform {:nodes {:1 nil}} options)))
        (is (= {:account-id "account-id"
                :start nil
                :nodes {:1 "node"}}
               (script/conform {:nodes {:1 "node"}} options)))
        (is (= {:account-id "account-id"
                :start nil
                :nodes {:1 {:id "1"
                            :account-id "account-id"
                            :script-id nil
                            :next nil}}}
               (script/conform {:nodes {:1 {}}} options)))
        (is (= {:id "script-id"
                :account-id "account-id"
                :start nil
                :nodes {:1 {:id "1"
                            :account-id "account-id"
                            :script-id "script-id"
                            :next nil}}}
               (script/conform {:id "script-id"
                                :nodes {:1 {}}} options)))
        (is (= {:id "script-id"
                :account-id "account-id"
                :start nil
                :nodes {:1 {:id "1"
                            :account-id "account-id"
                            :script-id "script-id"
                            :next nil
                            :node "payload"}}}
               (script/conform {:id "script-id"
                                :nodes {:1 {:node "payload"}}} options)))
        (is (= {:id "script-id"
                :account-id "account-id"
                :start nil
                :nodes {:3 {:id "3"
                            :account-id "account-id"
                            :script-id "script-id"
                            :next :2}}}
               (script/conform {:id "script-id"
                                :nodes {:3 {:next "2"}}} options)))))
    (testing "start"
      (let [enter-node (fn [node] (assoc node :action "enter-node"))]
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_script"
                        :message "Invalid script - missing start index"
                        :cause {}}}}
               (script/start {} enter-node)))
        (let [script {:id "script-id"
                      :nodes {:1 {}}}]
          (is (= {:ivr.routes/response
                  {:status 500
                   :data {:status 500
                          :status_code "invalid_script"
                          :message "Invalid script - missing start index"
                          :cause script}}}
                 (script/start script enter-node))))
        (let [script {:id "script-id"
                      :start "toto"
                      :nodes {:1 {}}}]
          (is (= {:ivr.routes/response
                  {:status 500
                   :data {:status 500
                          :status_code "invalid_script"
                          :message "Invalid script - missing start node"
                          :cause script}}}
                 (script/start script enter-node))))
        (let [script {:id "script-id"
                      :start :1
                      :nodes {:1 {:type "unknown"}}}]
          (is (= {:ivr.routes/response
                  {:status 500
                   :data {:status 500
                          :status_code "invalid_node"
                          :message "Invalid node - type"
                          :cause (get-in script [:nodes :1])}}}
                 (script/start script enter-node))))
        (let [script {:id "script-id"
                      :start :1
                      :nodes {:1 {:type "announcement"}}}]
          (is (= {:type "announcement"
                  :action "enter-node"}
                 (script/start script enter-node))))))))
