(ns ivr.models.node.transfert-list-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.node :as node]
            [ivr.models.node.transfert-list :as tl-node]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node.transfert-list))
   :after (fn [] (stest/unstrument 'ivr.models.node.transfert-list))})

(deftest transfert-list-node-model
  (testing "conform"
    (is (= {:type "transferlist"
            :next :42}
           (node/conform-type {:type "transferlist"
                               :failover "42"}))))
  (testing "enter"
    (let [store #(assoc % :store :query)
          verbs (fn [vs] {:verbs :create :data vs})
          deps {:store store :verbs verbs}
          context {:deps deps}
          base-node {:type "transferlist"
                     :id "node-id"
                     :account-id "account-id"
                     :script-id "script-id"
                     :dest "list-id"}]
      (is (= {:ivr.web/request
              {:store :query
               :type :ivr.store/get-account
               :id "account-id"
               :on-success
               [:ivr.models.node.transfert-list/eval-list-with-config
                {:node base-node :eval-list {}}]
               :on-error
               [:ivr.models.node.transfert-list/eval-list-with-config
                {:node base-node :eval-list {}}]}}
             (node/enter-type base-node context)))
      (testing "eval-list-with-config"
        (let [node base-node
              config {:ringing_tone "ringing"}
              params {}]
          (is (= {:ivr.web/request
                  {:method "POST"
                   :url "/smartccivrservices/account/account-id/destinationlist/list-id/eval",
                   :data {}
                   :on-success
                   [:ivr.models.node.transfert-list/transfert-call-to-list
                    {:node node
                     :config {:from "sip:anonymous@anonymous.invalid"
                              :timeout 10
                              :record false
                              :waitingurl "/smartccivr/twimlets/loopPlay/ringing"}}]
                   :on-error
                   [:ivr.models.node.transfert-list/eval-list-error
                    {:node node
                     :config {:from "sip:anonymous@anonymous.invalid"
                              :timeout 10
                              :record false
                              :waitingurl "/smartccivr/twimlets/loopPlay/ringing"}}]}}
                 (tl-node/eval-list-with-config
                   {:config config}
                   {:node node :account {}}
                   {:params params})))
          (is (= {:eval :list}
                 (get-in
                   (tl-node/eval-list-with-config
                     {:config config}
                     {:node node :eval-list {:eval :list} :account {}}
                     {:params params})
                   [:ivr.web/request :data])))
          (let [config (merge config {:ringingTimeoutSec 5
                                      :fromSda "CALLER"})
                params (merge params {:from "from-number"
                                      :to "to-number"})]
            (is (= {:from "from-number"
                    :timeout 5
                    :record false
                    :waitingurl "/smartccivr/twimlets/loopPlay/ringing"}
                   (get-in
                     (tl-node/eval-list-with-config
                       {:config config}
                       {:node node :account {}}
                       {:params params})
                     [:ivr.web/request :on-success 1 :config]))))
          (let [config (merge config {:ringingTimeoutSec 5
                                      :fromSda "CALLER"})
                account {:ringingTimeoutSec 15
                         :fromSda "CALLEE"
                         :record_enabled true
                         :ringing_tone "account-ringing"}
                params (merge params {:from "from-number"
                                      :to "to-number"})]
            (is (= {:from "to-number"
                    :timeout 15
                    :record true
                    :waitingurl "/smartccivr/twimlets/loopPlay/account-ringing"}
                   (get-in
                     (tl-node/eval-list-with-config
                       {:config config}
                       {:node node :account account}
                       {:params params})
                     [:ivr.web/request :on-success 1 :config]))))))
      (testing "transfert-call-to-list"
        (let [config {:from "from-number"
                      :timeout 15
                      :record true
                      :waitingurl "/url/waiting"}
              node base-node
              response #js {:body {:sda "eval-sda"
                                   :param1 "val1"
                                   :param2 "val2"}}]
          (is (= {:ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/dial-number
                           :number "eval-sda"
                           :callbackurl "/smartccivr/script/script-id/node/node-id/callback?_dstLst_param1=val1&amp;_dstLst_param2=val2"
                           :statusurl "/smartccivr/script/script-id/dialstatus"
                           :from "from-number"
                           :timeout 15
                           :record true
                           :waitingurl "/url/waiting"}]}}
                 (tl-node/transfert-call-to-list
                   deps
                   {:config config :node node
                    :response response})))))
      (testing "eval-list-error"
        (let [node (merge base-node {:next :42})]
          (is (= {:ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/redirect
                           :path "/smartccivr/script/script-id/node/42"}]}}
                 (tl-node/eval-list-error
                   deps {:node node}))))
        (let [node base-node]
          (is (= {:ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/hangup}]}}
                 (tl-node/eval-list-error
                   deps {:node node})))))))
  (testing "leave"
    (let [store #(assoc % :store :query)
          verbs (fn [vs] {:verbs :create :data vs})
          deps {:store store :verbs verbs}
          params {:_dstLst_param1 "val1"
                  :other "value"
                  :_dstLst_param2 "val2"}
          context {:params params :deps deps}
          base-node {:type "transferlist"
                     :id "node-id"
                     :account-id "account-id"
                     :script-id "script-id"
                     :dest "list-id"}]
      (is (= {:ivr.web/request
              {:store :query
               :type :ivr.store/get-account
               :id "account-id"
               :on-success
               [:ivr.models.node.transfert-list/eval-list-with-config
                {:node base-node :eval-list {:param1 "val1"
                                             :param2 "val2"}}]
               :on-error
               [:ivr.models.node.transfert-list/eval-list-with-config
                {:node base-node :eval-list {:param1 "val1"
                                             :param2 "val2"}}]}}
             (node/leave-type base-node context)))
      (let [context (merge context {:params {:dialstatus "completed"}})]
        (is (= {:ivr.routes/response
                {:verbs :create
                 :data [{:type :ivr.verbs/hangup}]}}
               (node/leave-type base-node context)))))))
