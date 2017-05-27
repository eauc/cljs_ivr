(ns ivr.models.script-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.script :as script]))

(deftest script-model
  (testing "events"
    (testing "resolve"
      (let [store #(assoc % :store :query)
            route-params {:account_id "account-id"
                          :script_id "script-id"}]
        (is (= {:ivr.web/request
                {:store :query
                 :type :ivr.store/get-script
                 :account-id "account-id"
                 :script-id "script-id"
                 :on-success [:ivr.models.script/resolve-success
                              {:account-id "account-id"}]
                 :on-error [:ivr.models.script/resolve-error
                            {:script-id "script-id"}]}}
               (script/resolve-event
                {:store store}
                [:resolve {:params route-params}])))))
    (testing "resolve-success"
      (let [response #js {:body {:start "42"}}]
        (is (= {:ivr.routes/params {:route :params
                                    :script {:start :42
                                             :account-id "account-id"
                                             :nodes {}}}
                :ivr.routes/next nil}
               (script/resolve-success
                {}
                [:success {:account-id "account-id"
                           :response response} {:params {:route :params}}])))))
    (testing "resolve-error"
      (let [response #js {:body {:start "42"}}]
        (is (= {:ivr.routes/response {:status 404
                                      :data {:status 404
                                             :status_code "script_not_found"
                                             :message "Script not found"
                                             :cause {:message "error"
                                                     :scriptid "script-id"}}}}
               (script/resolve-error
                {}
                [:success {:script-id "script-id"
                           :error {:message "error"}}])))))
    (testing "start-route"
      (let [enter-node #(assoc % :node :enter)
            script {:start :42
                    :nodes {:42 {:type "announcement"}}}
            call {:id "call-id"}]
        (is (= {:type "announcement", :node :enter}
               (script/start-route
                {:enter-node enter-node}
                [:start {:params {:script script :call call}}]))))))
  (testing "conform"
    (let [options {:account-id "account-id"}]
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
                                :nodes {:3 {:next "2"}}} options))))))
  (testing "start"
    (let [enter-node (fn [node] (assoc node :action "enter-node"))
          options {:enter-node enter-node}]
      (is (= {:ivr.routes/response
              {:status 500
               :data {:status 500
                      :status_code "invalid_script"
                      :message "Invalid script - missing start index"
                      :cause {}}}}
             (script/start {} options)))
      (let [script {:id "script-id"
                    :nodes {:1 {}}}]
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_script"
                        :message "Invalid script - missing start index"
                        :cause script}}}
               (script/start script options))))
      (let [script {:id "script-id"
                    :start "toto"
                    :nodes {:1 {}}}]
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_script"
                        :message "Invalid script - missing start node"
                        :cause script}}}
               (script/start script options))))
      (let [script {:id "script-id"
                    :start :1
                    :nodes {:1 {:type "unknown"}}}]
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_node"
                        :message "Invalid node - type"
                        :cause (get-in script [:nodes :1])}}}
               (script/start script options))))
      (let [script {:id "script-id"
                    :start :1
                    :nodes {:1 {:type "announcement"}}}]
        (is (= {:type "announcement"
                :action "enter-node"}
               (script/start script options)))))))
