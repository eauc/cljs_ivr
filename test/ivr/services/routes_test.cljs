(ns ivr.services.routes-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test.alpha :as stest]
            [ivr.services.routes :as routes]
            [ivr.services.routes.interceptor :as interceptor]))

(deftest routes-service
  (testing "after-route"
    (let [route {:req "req" :res "res" :next (fn [] nil)}
          id-effect-handler
          {:id-effect
           (fn [& args] (first args))}
          base-context {:coeffects {:db {:config-info {:config "config"}}
                                    :event [:name :payload route]}}]
      (testing "routes-effects"
        (testing "event does not contain valid route info"
          (let [context {:coeffects {:event [:name :payload :route]}
                         :effects {:id-effect "value"}}]
            (is (= context
                   (interceptor/after-route context [id-effect-handler])))))
        (let [first-args (atom nil)
              first-effect-handler
              {:first-effect
               (fn [& args]
                 (reset! first-args args))}
              second-args (atom nil)
              second-effect-handler
              {:second-effect
               (fn [& args]
                 (reset! second-args args))}]
          (testing "effect handler gets called with [context, route, effect-value]"
            (interceptor/after-route
              (merge base-context {:effects {:first-effect "value"}})
              first-effect-handler)
            (is (= [{:coeffects {:db {:config-info {:config "config"}}
                                 :event [:name :payload route]}
                     :effects {:first-effect "value"}}
                    route
                    "value"]
                   @first-args)))
          (testing "multiple handled effects"
            (let [result (interceptor/after-route
                           (merge base-context {:effects {:first-effect "1st-value"
                                                          :other "other-value"
                                                          :second-effect "2nd-value"}})
                           (merge first-effect-handler second-effect-handler))]
              (is (= [{:coeffects {:db {:config-info {:config "config"}}
                                   :event [:name :payload route]}
                       :effects {:first-effect "1st-value"
                                 :other "other-value"
                                 :second-effect "2nd-value"}}
                      route
                      "1st-value"]
                     @first-args))
              (is (= [{:coeffects {:db {:config-info {:config "config"}}
                                   :event [:name :payload route]}
                       :effects {:other "other-value"
                                 :second-effect "2nd-value"}}
                      route
                      "2nd-value"]
                     @second-args))))
          (testing "handled effect get removed from effets map"
            (is (= {}
                   (:effects
                    (interceptor/after-route
                      (merge base-context {:effects {:id-effect "value"}})
                      id-effect-handler)))))
          (testing "others effects get ignored"
            (is (= {:other "other-value"}
                   (:effects
                    (interceptor/after-route
                      (merge base-context
                             {:effects {:id-effect "value"
                                        :other "other-value"}})
                      id-effect-handler)))))
          (testing "multiple handled effects are all removed"
            (is (= {:coeffects {:db {:config-info {:config "config"}}
                                :event [:name :payload route]}
                    :effects {:other "other-value"}}
                   (interceptor/after-route
                     (merge base-context
                            {:effects {:first-effect "first-value"
                                       :other "other-value"
                                       :second-effect "second-value"}})
                     {:first-effect identity
                      :second-effect identity})))))))))
