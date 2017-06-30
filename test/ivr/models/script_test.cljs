(ns ivr.models.script-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.script :as script]))

(deftest script-model
  (testing "events"
    (testing "resolve-script"
      (let [store #(assoc % :store :query)
            route-params {"account_id" "account-id"
                          "script_id" "script-id"}]
        (is (= {:ivr.web/request
                {:store :query
                 :type :ivr.store/get-script
                 :account-id "account-id"
                 :script-id "script-id"
                 :on-success [:ivr.models.script/resolve-script-success
                              {:account-id "account-id"}]
                 :on-error [:ivr.models.script/resolve-script-error
                            {:script-id "script-id"}]}}
               (script/resolve-script-event
                 {:store store}
                 {:params route-params})))))


    (testing "resolve-script-success"
      (let [result {"start" "42"}]
        (is (= {:ivr.routes/params {"route" "params"
                                    "script" {"start" "42"
                                              "account_id" "account-id"
                                              "nodes" {}}}
                :ivr.routes/next nil}
               (script/resolve-script-success
                 {}
                 {:account-id "account-id" :script result}
                 {:params {"route" "params"}})))))


    (testing "resolve-script-error"
      (is (= {:ivr.routes/response {:status 404
                                    :data {:status 404
                                           :status_code "script_not_found"
                                           :message "Script not found"
                                           :cause {:message "error"
                                                   :scriptid "script-id"}}}}
             (script/resolve-script-error
               {}
               {:script-id "script-id"
                :error {:message "error"}}))))


    (testing "resolve-start-node"
      (is (= {:ivr.routes/response
              {:status 500
               :data {:status 500
                      :status_code "invalid_script"
                      :message "Invalid script - missing start index"
                      :cause {}}}}
             (script/resolve-start-node
               {} {:params {"script" {}}})))


      (let [script {"_id" "script-id"
                    "nodes" {"1" {}}}]
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_script"
                        :message "Invalid script - missing start index"
                        :cause script}}}
               (script/resolve-start-node
                 {} {:params {"script" script}}))))


      (let [script {"_id" "script-id"
                    "start" "toto"
                    "nodes" {"1" {}}}]
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_script"
                        :message "Invalid script - missing node"
                        :cause script}}}
               (script/resolve-start-node
                 {} {:params {"script" script}}))))


      (let [script {"_id" "script-id"
                    "start" "1"
                    "nodes" {"1" {"type" "announcement"}}}
            params {"script" script}]
        (is (= {:ivr.routes/next nil,
                :ivr.routes/params {"script" script
                                    "node" {"type" "announcement"}
                                    "action" :enter}}
               (script/resolve-start-node
                 {} {:params params})))))


    (testing "resolve-node"
      (let [script {"_id" "script-id"
                    "nodes" {"other" {}}}]
        (is (= {:ivr.routes/response
                {:status 500
                 :data {:status 500
                        :status_code "invalid_script"
                        :message "Invalid script - missing node"
                        :cause script}}}
               (script/resolve-node
                 {} {:action :enter}
                 {:params {"script" script
                           "node_id" "42"}}))))


      (let [script {"_id" "script-id"
                    "nodes" {"42" {"type" "announcement"}}}
            params {"script" script
                    "node_id" "42"}]
        (is (= {:ivr.routes/params
                {"script" script
                 "node_id" "42"
                 "node" {"type" "announcement"}
                 "action" :enter}
                :ivr.routes/next nil}
               (script/resolve-node
                 {} {:action :enter}
                 {:params params})))
        (is (= {:ivr.routes/params
                {"script" script
                 "node_id" "42"
                 "node" {"type" "announcement"}
                 "action" :leave}
                :ivr.routes/next nil}
               (script/resolve-node
                 {} {:action :leave}
                 {:params params})))))


    (let [call {:info {:id "call-id"}
                :action-data {"action" "data"}}
          node {"type" "announcement"}
          params {"call" call "node" node}]

      (testing "enter-node-route"
        (let [enter-node #(assoc %1 "node" "enter" "context" %2)
              coeffects {:enter-node enter-node :store "store" :verbs "verbs"}]
          (is (= {"type" "announcement"
                  "node" "enter"
                  "context" {:call call
                             :deps coeffects
                             :params params}}
                 (script/enter-node-route coeffects {:params params})))))


      (testing "leave-node-route"
        (let [leave-node #(assoc %1 "node" "leave" "context" %2)
              coeffects {:leave-node leave-node :store "store" :verbs "verbs"}]
          (is (= {"type" "announcement"
                  "node" "leave"
                  "context" {:call call
                             :deps coeffects
                             :params params}}
                 (script/leave-node-route coeffects {:params params})))))))


  (testing "conform"
    (let [options {:account-id "account-id"}]


      (testing "start"
        (is (= {"account_id" "account-id"
                "nodes" {}}
               (script/conform {} options)))
        (is (= {"account_id" "account-id"
                "start" "42"
                "nodes" {}}
               (script/conform {"start" "42"} options)))
        (is (= {"account_id" "account-id"
                "start" "42"
                "nodes" {}}
               (script/conform {"start" 42} options)))
        (is (= {"account_id" "account-id"
                "start" "false"
                "nodes" {}}
               (script/conform {"start" false} options))))


      (testing "nodes"
        (is (= {"account_id" "account-id"
                "nodes" {"1" nil}}
               (script/conform {"nodes" {"1" nil}} options)))
        (is (= {"account_id" "account-id"
                "nodes" {"1" "node"}}
               (script/conform {"nodes" {"1" "node"}} options)))
        (is (= {"account_id" "account-id"
                "nodes" {"1" {"id" "1"
                              "account_id" "account-id"
                              "script_id" nil}}}
               (script/conform {"nodes" {"1" {}}} options)))
        (is (= {"_id" "script-id"
                "account_id" "account-id"
                "nodes" {"1" {"id" "1"
                              "account_id" "account-id"
                              "script_id" "script-id"}}}
               (script/conform {"_id" "script-id"
                                "nodes" {"1" {}}} options)))
        (is (= {"_id" "script-id"
                "account_id" "account-id"
                "nodes" {"1" {"id" "1"
                              "account_id" "account-id"
                              "script_id" "script-id"
                              "node" "payload"}}}
               (script/conform {"_id" "script-id"
                                "nodes" {"1" {"node" "payload"}}} options)))
        (is (= {"_id" "script-id"
                "account_id" "account-id"
                "nodes" {"3" {"id" "3"
                              "account_id" "account-id"
                              "script_id" "script-id"
                              "next" "2"}}}
               (script/conform {"_id" "script-id"
                                "nodes" {"3" {"next" "2"}}} options)))))))
