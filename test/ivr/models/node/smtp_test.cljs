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
    (let [services #(assoc % :services :query)
          verbs (fn [vs] {:verbs :create :data vs})
          deps {:services services
                :verbs verbs}
          call {:action-data {:action :data}}
          context {:call call
                   :deps deps}
          node {:type "smtp"
                :account-id "account-id"
                :script-id "script-id"
                :to "to"
                :subject "subject"
                :text "text"
                :attachment "attachment"}]
      (is (= {:ivr.web/request
              {:services :query
               :type :ivr.services/send-mail
               :account-id "account-id"
               :context {:action :data}
               :options {:subject "subject"
                         :to "to"
                         :attachment "attachment"
                         :text "text"}
               :on-success
               [:ivr.models.node.smtp/send-mail-success
                {:node node}],
               :on-error
               [:ivr.models.node.smtp/send-mail-error
                {:node node}]}
              :ivr.routes/response
              {:verbs :create
               :data [{:type :ivr.verbs/hangup}]}}
             (node/enter-type node context)))
      (is (= {:verbs :create
              :data [{:type :ivr.verbs/redirect
                      :path "/smartccivr/script/script-id/node/42"}]}
             (:ivr.routes/response
              (node/enter-type
                (merge node {:next :42})
                context)))))))
