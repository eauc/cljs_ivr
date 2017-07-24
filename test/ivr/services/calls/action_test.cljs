(ns ivr.services.calls.action-test
  (:require [cljs.spec.test.alpha :as stest]
            [clojure.test :as test :refer-macros [deftest is testing use-fixtures]]
            [ivr.models.call :as call]
            [ivr.services.calls.action :as action]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.services.calls.action))
   :after (fn [] (stest/unstrument 'ivr.services.calls.action))})

(deftest calls-action-service

  (testing "start action"
    (let [deps {:db {:calls {}} :call-time-now 71}
          event {:call-id "call-id" :action {:node :action}}]

      (testing "call does not exist, do nothing"
        (is (= {}
               (action/start-action deps event))))


      (testing "call does exist"

        (testing "update ongoing action"
          (let [call (call/info->call {:id "call-id" :time "call-time"})
                db (call/db-insert-call {} call)
                deps (assoc deps :db db)]
            (is (= {:ivr.call/update
                    {:id "call-id"
                     :action-ongoing {:action {:node :action}
                                      :start-time 71}}}
                   (action/start-action deps event)))


            (testing "call has previous ongoing action, emit ticket"
              (let [call (assoc call :action-ongoing {:action {:action :previous}
                                                      :start-time 42})
                    db (call/db-insert-call {} call)
                    deps (assoc deps :db db)]
                (is (= {:producer "IVR"
                        :subject "ACTION"
                        :callid "call-id"
                        :callTime "call-time"
                        :time 71
                        :duration 29
                        :action {:action :previous}}
                       (:ivr.ticket/emit
                        (action/start-action deps event))))))))))))
