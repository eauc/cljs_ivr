(ns ivr.services.routes-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.services.routes :as routes]))

(deftest routes-service
  (testing "before-route"
    (testing "remove route from event and put in coeffects"
      (is (= {:coeffects {:event [:name :payload]
                          :route :route-params}}
             (routes/before-route
              {:coeffects {:event [:name :route-params :payload]}}))))
    (testing "invalid event"
      (is (= {:coeffects {:event [:name]
                          :route nil}}
             (routes/before-route
              {:coeffects {:event [:name]}})))))
  (testing "after-route"
    (let [route {:req "req" :res "res" :next "next"}
          base-context {:coeffects {:db {:config-info {:config "config"}}
                                    :route route}}]
      (testing "ivr.routes/next effect"
        (is (= (merge base-context
                      {:effects {:ivr.routes/next {:next "next"}}})
               (routes/after-route
                (merge base-context
                       {:effects {:ivr.routes/next nil}})))))
      (testing "ivr.routes/response effect"
        (is (= (merge base-context
                      {:effects {:ivr.routes/response {:data "data"
                                                       :res "res"}}})
               (routes/after-route
                (merge base-context
                       {:effects {:ivr.routes/response {:data "data"}}}))))
        (is (= (merge base-context
                      {:effects {:ivr.routes/response {:data "data"
                                                       :res "res"}}})
               (routes/after-route
                (merge base-context
                       {:effects {:ivr.routes/response {:data "data"
                                                        :res "other"}}})))))
      (testing "ivr.web/request effect"
        (is (= (merge base-context
                      {:effects {:ivr.web/request {:url "/url"
                                                   :config "config"}}})
               (routes/after-route
                (merge base-context
                       {:effects {:ivr.web/request {:url "/url"}}}))))
        (testing "on-success route insertion"
          (is (= (merge base-context
                        {:effects {:ivr.web/request {:url "/url"
                                                     :on-success []
                                                     :config "config"}}})
                 (routes/after-route
                  (merge base-context
                         {:effects {:ivr.web/request {:url "/url"
                                                      :on-success []}}}))))
          (is (= (merge base-context
                        {:effects {:ivr.web/request {:url "/url"
                                                     :on-success [:event route]
                                                     :config "config"}}})
                 (routes/after-route
                  (merge base-context
                         {:effects {:ivr.web/request {:url "/url"
                                                      :on-success [:event]}}}))))
          (is (= (merge base-context
                        {:effects {:ivr.web/request {:url "/url"
                                                     :on-success [:event route :payload]
                                                     :config "config"}}})
                 (routes/after-route
                  (merge base-context
                         {:effects {:ivr.web/request {:url "/url"
                                                      :on-success [:event :payload]}}})))))
        (testing "on-error route insertion"
          (is (= (merge base-context
                        {:effects {:ivr.web/request {:url "/url"
                                                     :on-error []
                                                     :config "config"}}})
                 (routes/after-route
                  (merge base-context
                         {:effects {:ivr.web/request {:url "/url"
                                                      :on-error []}}}))))
          (is (= (merge base-context
                        {:effects {:ivr.web/request {:url "/url"
                                                     :on-error [:event route]
                                                     :config "config"}}})
                 (routes/after-route
                  (merge base-context
                         {:effects {:ivr.web/request {:url "/url"
                                                      :on-error [:event]}}}))))
          (is (= (merge base-context
                        {:effects {:ivr.web/request {:url "/url"
                                                     :on-error [:event route :payload]
                                                     :config "config"}}})
                 (routes/after-route
                  (merge base-context
                         {:effects {:ivr.web/request {:url "/url"
                                                      :on-error [:event :payload]}}})))))))))
