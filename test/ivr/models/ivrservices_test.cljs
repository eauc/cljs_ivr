(ns ivr.models.ivrservices-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.ivrservices :as services]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.ivrservices))
   :after (fn [] (stest/unstrument 'ivr.models.ivrservices))})

(deftest ivrservices-model-test

  (testing "unknown query"
    (is (= {:ivr.routes/response
            {:status 500
             :data {:status 500
                    :status_code "invalid_services_query"
                    :message "Invalid IVR Services query - type"
                    :cause {:type :ivr.services/unknown :params :values}}}}
           (services/query {:type :ivr.services/unknown :params :values}))))


  (testing "eval routing rule"
    (let [request (services/query {:type :ivr.services/eval-routing-rule
                                   :account-id "account-id"
                                   :route-id "route-id"
                                   :on-success [:success {:payload :data}]
                                   :on-error [:error]})]
      (is (= {:method "POST"
              :url "/smartccivrservices/account/account-id/routingrule/route-id/eval"
              :on-error [:error]}
             (dissoc request :on-success)))


      (testing "eval route success"
        (let [on-success (:on-success request)]
          (is (= [:success {:payload :data, :route-value "route-value"}]
                 (on-success #js {:body "route-value"})))))))


  (testing "send mail"
    (let [request (services/query {:type :ivr.services/send-mail
                                   :account-id "account-id"
                                   :context {:context :data}
                                   :options {:mail :options}
                                   :on-success [:success]
                                   :on-error [:error]})]
      (is (= {:method "POST"
              :url "/smartccivrservices/account/account-id/mail"
              :data {:context {:context :data}
                     :mailOptions {:mail :options}}
              :on-success [:success]
              :on-error [:error]}
             request))))


  (testing "eval destination list"
    (let [request (services/query {:type :ivr.services/eval-destination-list
                                   :account-id "account-id"
                                   :list-id "list-id"
                                   :data {:eval :list}
                                   :on-success [:success]
                                   :on-error [:error]})]
      (is (= {:method "POST"
              :url "/smartccivrservices/account/account-id/destinationlist/list-id/eval"
              :data {:eval :list}
              :on-error [:error]}
             (dissoc request :on-success)))


      (testing "eval destination list success"
        (let [on-success (:on-success request)]
          (is (= [:success {:list-value "list-value"}]
                 (on-success #js {:body "list-value"}))))))))
