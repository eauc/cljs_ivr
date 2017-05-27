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
      (let [enter-node #(assoc %1 :node :enter :params %2)
            coeffects {:enter-node enter-node :store "store" :verbs "verbs"}
            call {:info {:id "call-id"}
                  :action-data {:action :data}}]
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_script"
                        :message "Invalid script - missing start index"
                        :cause {}}}}
               (script/start-route
                coeffects
                [:enter {:params {:script {}}}])))
        (let [script {:id "script-id"
                      :nodes {:1 {}}}]
          (is (= {:ivr.routes/response
                  {:status 500
                   :data {:status 500
                          :status_code "invalid_script"
                          :message "Invalid script - missing start index"
                          :cause script}}}
                 (script/start-route
                  coeffects
                  [:enter {:params {:script script}}]))))
        (let [script {:id "script-id"
                      :start "toto"
                      :nodes {:1 {}}}]
          (is (= {:ivr.routes/response
                  {:status 500
                   :data {:status 500
                          :status_code "invalid_script"
                          :message "Invalid script - missing start node"
                          :cause script}}}
                 (script/start-route
                  coeffects
                  [:enter {:params {:script script}}]))))
        (let [script {:id "script-id"
                      :start :1
                      :nodes {:1 {:type "announcement"}}}]
          (is (= {:type "announcement"
                  :node :enter
                  :params {:action-data {:action :data}
                           :call-id "call-id"
                           :store "store"
                           :verbs "verbs"}}
                 (script/start-route
                  coeffects
                  [:enter {:params {:script script
                                    :call call}}]))))))
    (testing "leave-node-route"
      (let [leave-node #(assoc %1 :node :leave :params %2)
            coeffects {:leave-node leave-node :store "store" :verbs "verbs"}
            call {:info {:id "call-id"}
                  :action-data {:action :data}}]
        (let [script {:nodes {:other {:type "announcement"}}}]
          (is (= {:ivr.routes/response
                  {:status 500
                   :data {:status 500
                          :status_code "invalid_script"
                          :message "Invalid script - missing node"
                          :cause script}}}
                 (script/leave-node-route
                  coeffects
                  [:leave {:params {:script script
                                    :node_id "42"
                                    :call call}}]))))
        (let [script {:nodes {:42 {:type "announcement"}}}]
          (is (= {:type "announcement"
                  :node :leave
                  :params {:action-data {:action :data}
                           :call-id "call-id"
                           :store "store"
                           :verbs "verbs"}}
                 (script/leave-node-route
                  coeffects
                  [:leave {:params {:script script
                                    :node_id "42"
                                    :call call}}])))))))
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
                                :nodes {:3 {:next "2"}}} options)))))))
