(ns ivr.models.node.announcement-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.node :as node]
            [ivr.models.node.announcement :as announcement-node]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node.announcement))
   :after (fn [] (stest/unstrument 'ivr.models.node.announcement))})

(deftest announcement-node-model
  (let [verbs (fn [actions] {:verbs "mock"
                             :actions actions})]
    (testing "conform"
      (is (= {:type "announcement"
              :id "node-id"
              :account-id "account-id"
              :script-id "script-id"
              :next nil}
             (node/conform {:type "announcement"}
                           {:id "node-id"
                            :account-id "account-id"
                            :script-id "script-id"}))))
    (testing "enter"
      (let [store (fn [query] {:store "mock"
                               :query query})
            verbs (fn [actions] {:verbs "mock"
                                 :actions actions})
            options {:action-data {:action "data"}
                     :call-id "call-id"
                     :store store
                     :verbs verbs}]
        (testing "preset"
          (let [node {:type "announcement"
                      :account-id "account-id"
                      :script-id "script-id"
                      :soundname "sound"
                      :preset {:type :ivr.node.announcement.preset/set
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
                      :preset {:type :ivr.node.announcement.preset/copy
                               :to :to_var
                               :from :action}}]
            (is (= {:call-id "call-id"
                    :data {:action "data"
                           :to_var "data"}}
                   (:ivr.call/action-data
                    (node/enter-type node options))))))
        (testing "enabled"
          (let [node {:type "announcement"
                      :account-id "account-id"
                      :script-id "script-id"
                      :soundname "sound"}]
            (is (= {:ivr.web/request
                    {:store "mock"
                     :query {:type :ivr.store/get-sound-by-name
                             :name "sound"
                             :account-id "account-id"
                             :script-id "script-id"
                             :on-success
                             [:ivr.models.node.announcement/play-sound options]}}}
                   (node/enter-type node options)))))
        (testing "disabled - no next"
          (let [node {:type "announcement"
                      :account-id "account-id"
                      :script-id "script-id"
                      :soundname "sound"
                      :disabled true}]
            (is (= {:ivr.routes/response
                    {:verbs "mock"
                     :actions [{:type :ivr.verbs/hangup}]}}
                   (node/enter-type node options)))))
        (testing "disabled - next"
          (let [node {:type "announcement"
                      :account-id "account-id"
                      :script-id "script-id"
                      :soundname "sound"
                      :disabled true
                      :next :2}]
            (is (= {:ivr.routes/response
                    {:verbs "mock"
                     :actions [{:type :ivr.verbs/redirect
                                :path "/smartccivr/script/script-id/node/2"}]}}
                   (node/enter-type node options)))))))
    (testing "play-sound"
      (testing "simple announcement"
        (is (= {:ivr.routes/response
                {:verbs "mock"
                 :actions [{:type :ivr.verbs/play
                            :path "sound-url"}]}}
               (announcement-node/play-sound "sound-url" {:no_barge true} verbs))))
      (testing "with dtmf catch"
        (is (= {:ivr.routes/response
                {:verbs "mock"
                 :actions [{:type :ivr.verbs/gather
                            :numdigits 1
                            :timeout 1
                            :callbackurl "/smartccivr/script/script-id/node/node-id/callback"
                            :play ["sound-url"]}]}}
               (announcement-node/play-sound "sound-url"
                                             {:id "node-id" :script-id "script-id"}
                                             verbs)))))))
