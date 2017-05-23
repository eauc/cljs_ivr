(ns ivr.services.routes-test
	(:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[ivr.services.routes :as routes]
						[cljs.spec :as spec]))

(deftest routes-service
	(testing "after-route"
		(let [route {:req "req"}
					id-effect-handler {:key :id-effect
														 :handler (fn [& args] (first args))}
					base-context {:coeffects {:db {:config-info {:config "config"}}
																		:event [:name :payload route]}}]
			(testing "routes-effects"
				(testing "event does not contain valid route info"
					(let [context {:coeffects {:event [:name :payload :route]}
												 :effects {:id-effect "value"}}]
						(is (= context
									 (routes/after-route context [id-effect-handler])))))
				(let [first-args (atom nil)
							first-effect-handler
							{:key :first-effect
							 :handler (fn [& args]
													(reset! first-args args)
													(update-in (first args)
																		 [:coeffects :event 2]
																		 assoc :first (last args)))}
							second-args (atom nil)
							second-effect-handler
							{:key :second-effect
							 :handler (fn [& args]
													(reset! second-args args)
													(update-in (first args)
																		 [:coeffects :event 2]
																		 assoc :second (last args)))}]
					(testing "effect handler gets called with [context, route, effect-value]"
						(routes/after-route (merge base-context {:effects {:first-effect "value"}})
																[first-effect-handler])
						(is (= '({:coeffects {:db {:config-info {:config "config"}}
																	:event [:name :payload {:req "req"}]}
											:effects {:first-effect "value"}}
										 {:req "req"}
										 "value")
									 @first-args)))
					(testing "multiple handled effects, next handlers are called with result from previous handlers"
						(let [result (routes/after-route
													 (merge base-context {:effects {:first-effect "1st-value"
																													:other "other-value"
																													:second-effect "2nd-value"}})
													 [first-effect-handler second-effect-handler])]
							(is (=	[:name :payload {:req "req"
																			 :first "1st-value"
																			 :second "2nd-value"}]
											(get-in result [:coeffects :event])))
							(is (= '({:coeffects {:db {:config-info {:config "config"}}
																		:event [:name :payload {:req "req"}]}
												:effects {:first-effect "1st-value"
																	:other "other-value"
																	:second-effect "2nd-value"}}
											 {:req "req"}
											 "1st-value")
										 @first-args))
							(is (= '({:coeffects {:db {:config-info {:config "config"}}
																		:event [:name :payload {:req "req" :first "1st-value"}]}
												:effects {:other "other-value"
																	:second-effect "2nd-value"}}
											 {:req "req" :first "1st-value"}
											 "2nd-value")
										 @second-args))))
					(testing "next dispatch gets called with route updated by handlers"
						(let [result (routes/after-route
													 (merge base-context {:effects {:first-effect "1st-value"
																													:dispatch [:next-event :payload]}})
													 [first-effect-handler])]
							(is (= [:next-event :payload {:req "req" :first "1st-value"}]
										 (get-in result [:effects :dispatch]) ))))
          (testing "handled effect get removed from effets map"
            (is (= {}
                   (:effects
                    (routes/after-route
                      (merge base-context {:effects {:id-effect "value"}})
                      [id-effect-handler])))))
          (testing "others effects get ignored"
            (is (= {:other "other-value"}
                   (:effects
                    (routes/after-route
                      (merge base-context
                             {:effects {:id-effect "value"
                                        :other "other-value"}})
                      [id-effect-handler])))))
          (testing "multiple handled effects are all removed"
            (is (= {:coeffects {:db {:config-info {:config "config"}}
                                :event [:name :payload {:req "req"}]}
                    :effects {:other "other-value"}}
                   (routes/after-route (merge base-context
                                              {:effects {:first-effect "first-value"
                                                         :other "other-value"
                                                         :second-effect "second-value"}})
                                       [{:key :first-effect :handler identity}
                                        {:key :second-effect :handler identity}])))))))))
