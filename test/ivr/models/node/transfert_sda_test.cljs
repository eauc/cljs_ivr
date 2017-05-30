(ns ivr.models.node.transfert-sda-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.node :as node]
            [ivr.models.node.transfert-sda :as ts-node]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node.transfert-sda))
   :after (fn [] (stest/unstrument 'ivr.models.node.transfert-sda))})

(deftest transfert-sda-node-model
  (testing "conform"
    (is (= {:type "transfersda"}
           (node/conform-type {:type "transfersda"})))
    (is (= {:type "transfersda"
            :case {:busy :42
                   :no-answer nil}}
           (node/conform-type {:type "transfersda"
                               :case {:busy "42"}}))))
  (let [store #(assoc % :store :query)
        node {:type "transfersda"
              :id "node-id"
              :account-id "account-id"
              :script-id "script-id"
              :dest "dest-sda"}
        verbs (fn [vs] {:verbs :create :data vs})
        options {:store store
                 :verbs verbs}]
    (testing "enter"
      (is (= {:ivr.web/request
              {:store :query
               :type :ivr.store/get-account
               :id "account-id"
               :on-success
               [:ivr.models.node.transfert-sda/transfert-sda-with-config
                {:node node :options options}]
               :on-error
               [:ivr.models.node.transfert-sda/transfert-sda-with-config
                {:node node :options options}]}}
             (node/enter-type node options)))
      (testing "transfert-sda-with-config"
        (let [config {:fromSda "CALLEE"
                      :ringingTimeoutSec 15}
              response #js {:body {:fromSda "CALLER"
                                   :record_enabled true
                                   :ringing_tone "ringing"}}
              params {:from "from-number"
                      :to "to-number"}]
          (is (= {:ivr.routes/response
                  {:verbs :create
                   :data [{:type :ivr.verbs/dial-number
                           :number "dest-sda"
                           :from "from-number"
                           :timeout 15
                           :record true
                           :callbackurl "/smartccivr/script/script-id/node/node-id/callback"
                           :statusurl "/smartccivr/script/script-id/dialstatus"
                           :waitingurl "/smartccivr/twimlets/loopPlay/ringing"}]}}
                 (ts-node/transfert-sda-with-config
                  {:config config}
                  [:event
                   {:node node :options options :response response}
                   {:params params}]))))))
    (testing "leave"
      (let [node (merge node {:next :42})
            params {:dialstatus "completed"}
            options (assoc options :params params)]
        (is (= {:ivr.routes/response
                {:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
               (node/leave-type node options))))
      (let [node (merge node {:case {:busy :42
                                     :no-answer :71}})
            params {:dialstatus "busy"}
            options (assoc options :params params)]
        (is (= {:ivr.routes/response
                {:verbs :create,
                 :data
                 [{:type :ivr.verbs/redirect,
                   :path "/smartccivr/script/script-id/node/42"}]}}
               (node/leave-type node options))))
      (let [node (merge node {:case {:busy :42
                                     :no-answer :71}})
            params {:dialstatus "no-answer"}
            options (assoc options :params params)]
        (is (= {:ivr.routes/response
                {:verbs :create,
                 :data
                 [{:type :ivr.verbs/redirect,
                   :path "/smartccivr/script/script-id/node/71"}]}}
               (node/leave-type node options))))
      (let [node (merge node {:case {:busy :42
                                     :other :71}})
            params {:dialstatus "no-answer"}
            options (assoc options :params params)]
        (is (= {:ivr.routes/response
                {:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
               (node/leave-type node options))))
      (let [node (merge node {:case {:busy :42
                                     :other :71}})
            params {:dialstatus "failed"}
            options (assoc options :params params)]
        (is (= {:ivr.routes/response
                {:verbs :create,
                 :data
                 [{:type :ivr.verbs/redirect,
                   :path "/smartccivr/script/script-id/node/71"}]}}
               (node/leave-type node options)))))))