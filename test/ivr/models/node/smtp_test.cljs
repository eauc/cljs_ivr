(ns ivr.models.node.smtp-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.node :as node]
            [ivr.models.node.smtp :as smtp-node]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node.smtp))
   :after (fn [] (stest/unstrument 'ivr.models.node.smtp))})

(deftest smtp-node-model
  (testing "enter"
    (let [action-data {:action :data}
          verbs (fn [vs] {:verbs :create :data vs})
          options {:action-data action-data
                   :verbs verbs}
          node {:type "smtp"
                :account-id "account-id"
                :script-id "script-id"
                :to "to"
                :subject "subject"
                :text "text"
                :attachment "attachment"}]
      (is (= {:ivr.web/request
              {:method "POST"
               :url "/smartccivrservices/account/account-id/mail"
               :data {:context action-data
                      :mailOptions {:subject "subject"
                                    :to "to"
                                    :attachment "attachment"
                                    :text "text"}}
               :on-success
               [:ivr.models.node.smtp/send-mail-success
                {:node node}]
               :on-error
               [:ivr.models.node.smtp/send-mail-error
                {:node node}]}
              :ivr.routes/response
              {:verbs :create
               :data [{:type :ivr.verbs/hangup}]}}
             (node/enter-type node options)))
      (is (= {:verbs :create
              :data [{:type :ivr.verbs/redirect
                      :path "/smartccivr/script/script-id/node/42"}]}
             (:ivr.routes/response
              (node/enter-type
               (merge node {:next :42})
               options)))))))
