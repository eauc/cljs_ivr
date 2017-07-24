(ns ivr.models.node.announcement-test
  (:require [cljs.spec.test.alpha :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.node :as node]
            [ivr.models.node.announcement :as announcement-node]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node.announcement))
   :after (fn [] (stest/unstrument 'ivr.models.node.announcement))})

(deftest announcement-node-model
  (let [call {:action-data {:action "data"}
              :info {:id "call-id"}}
        store (fn [query] {:store "mock"
                           :query query})
        verbs (fn [actions] {:verbs "mock"
                             :actions actions})
        deps {:store store :verbs verbs}
        context {:call call :deps deps :params {}}]


    (testing "conform"
      (is (= {"type" "announcement"
              "id" "node-id"
              "account_id" "account-id"
              "script_id" "script-id"
              "preset" [{:to "to_var" :value "to_value"}]}
             (node/conform {"type" "announcement"
                            "preset" {"value" "to_value"
                                      "varname" "to_var"}}
                           {:id "node-id"
                            :account-id "account-id"
                            :script-id "script-id"}))))


    (testing "enter"


      (testing "enabled"
        (let [node {"type" "announcement"
                    "account_id" "account-id"
                    "script_id" "script-id"
                    "soundname" "sound"}]
          (is (= {:ivr.web/request
                  {:store "mock"
                   :query {:type :ivr.store/get-sound-by-name
                           :name "sound"
                           :account-id "account-id"
                           :script-id "script-id"
                           :on-success
                           [:ivr.models.node.announcement/play-sound
                            {:node node}]}}}
                 (node/enter-type node context)))))


      (testing "disabled - no next"
        (let [node {"type" "announcement"
                    "account_id" "account-id"
                    "script_id" "script-id"
                    "soundname" "sound"
                    "disabled" true}]
          (is (= {:ivr.routes/response
                  {:verbs "mock"
                   :actions [{:type :ivr.verbs/hangup}]}}
                 (node/enter-type node context)))))


      (testing "disabled - next"
        (let [node {"type" "announcement"
                    "account_id" "account-id"
                    "script_id" "script-id"
                    "soundname" "sound"
                    "disabled" true
                    "next" "2"}]
          (is (= {:ivr.routes/response
                  {:verbs "mock"
                   :actions [{:type :ivr.verbs/redirect
                              :path "/smartccivr/script/script-id/node/2"}]}}
                 (node/enter-type node context))))))


    (testing "play-sound"
      (let [node {"type" "announcement"
                  "id" "node-id"
                  "account_id" "account-id"
                  "script_id" "script-id"
                  "soundname" "son1"}]


        (testing "simple announcement / no next"
          (is (= {:ivr.routes/response
                  {:verbs "mock"
                   :actions [{:type :ivr.verbs/play
                              :path "sound-url"}
                             {:type :ivr.verbs/hangup}]}}
                 (announcement-node/play-sound
                   deps
                   {:sound-url "sound-url"
                    :node (merge node {"no_barge" true})}))))


        (testing "with dtmf catch / next node"
          (let [node (assoc node "next" "2")]
            (is (= {:ivr.routes/response
                    {:verbs "mock"
                     :actions [{:type :ivr.verbs/gather
                                :numdigits 1
                                :timeout 1
                                :callbackurl "/smartccivr/script/script-id/node/node-id/callback"
                                :play ["sound-url"]}
                               {:type :ivr.verbs/redirect
                                :path "/smartccivr/script/script-id/node/2"}]}}
                   (announcement-node/play-sound
                     deps
                     {:sound-url "sound-url"
                      :node node})))))))


    (testing "leave"


      (testing "no next"
        (let [node {"type" "announcement"
                    "account_id" "account-id"
                    "script_id" "script-id"}]
          (is (= {:ivr.routes/response
                  {:verbs "mock"
                   :actions [{:type :ivr.verbs/hangup}]}}
                 (node/leave-type node context)))))


      (testing "next"
        (let [node {"type" "announcement"
                    "account_id" "account-id"
                    "script_id" "script-id"
                    "next" "2"}]
          (is (= {:ivr.routes/response
                  {:verbs "mock"
                   :actions [{:type :ivr.verbs/redirect
                              :path "/smartccivr/script/script-id/node/2"}]}}
                 (node/leave-type node context))))))))
