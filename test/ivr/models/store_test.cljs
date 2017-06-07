(ns ivr.models.store-test
	(:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[cljs.spec.test :as stest]
						[ivr.models.store :as store]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.store))
	 :after (fn [] (stest/unstrument 'ivr.models.store))})

(deftest store-model
	(testing "query"


		(testing "get-account"
			(let [query {:type :ivr.store/get-account
									 :id "account-id"
									 :on-success [:success {:payload :data}]
									 :on-error [:error]}
						request (store/query query)]
				(is (= {:method "GET"
								:url "/cloudstore/account/account-id"
								:on-error [:error]}
							 (dissoc request :on-success)))
				(testing "on-success"
					(let [on-success (get request :on-success)]
						(is (= [:success {:payload :data :account {:account :data}}]
									 (on-success #js {:body {:account :data}})))))))


		(testing "get-script"
			(let [query {:type :ivr.store/get-script
									 :account-id "account-id"
									 :script-id "script-id"
									 :on-success [:success {:payload :data}]
									 :on-error [:error]}
						request (store/query query)]
				(is (= {:method "GET"
								:url "/cloudstore/account/account-id/script/script-id"
								:on-error [:error]}
							 (dissoc request :on-success)))
				(testing "on-success"
					(let [on-success (get request :on-success)]
						(is (= [:success {:payload :data :script {:script :data}}]
									 (on-success #js {:body {:script :data}})))))))


		(testing "get-sound-by-name"
			(let [query {:type :ivr.store/get-sound-by-name
									 :name "sound"
									 :account-id "account-id"
									 :script-id "script-id"
									 :on-success [:success {:payload :data}]}
						request (store/query query)]
				(is (= {:method "GET"
								:url "/cloudstore/account/account-id/file"
								:query {:query {:filename "sound"
																:metadata.type "scriptSound"
																:metadata.script "script-id"}}}
							 (dissoc request :on-success :on-error)))


				(testing "get-sound-by-name on-success"
					(let [on-success (get request :on-success)]
						(testing "no result"
							(let [result {"meta" {"total_count" 0} "objects" []}]
								(is (= [:ivr.routes/error
												{:status 500
												 :statusCode "sound_not_found"
												 :message "Sound not found"
												 :cause {:account-id "account-id"
																 :script-id "script-id"
																 :name "sound"}}]
											 (on-success #js {:body result})))))
						(testing "ok"
							(let [result {"meta" {"total_count" 2}
														"objects" [{"_id" "42"}
																			 {"_id" "54"}]}]
								(is (= [:success {:payload :data :sound-url "/cloudstore/file/42"}]
											 (on-success #js {:body result})))))))


				(testing "get-file-error"
					(let [on-error (get request :on-error)]
						(is (= [:ivr.routes/error
										{:status 404
										 :status_code "file_not_found"
										 :message "File not found"
										 :cause {:type :ivr.store/get-sound-by-name,
														 :name "sound",
														 :account-id "account-id",
														 :script-id "script-id",
														 :url "/cloudstore/account/account-id/file",
														 :message "error message"}}]
									 (on-error {:message "error message"})))))))))
