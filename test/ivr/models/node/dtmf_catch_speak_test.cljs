(ns ivr.models.node.dtmf-catch-speak-test
	(:require [cljs.spec.test.alpha :as stest]
						[clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
						[ivr.models.node.dtmf-catch-speak :as dc-speak]))

(use-fixtures :once
	{:before (fn [] (stest/instrument 'ivr.models.node.dtmf-catch-speak))
	 :after (fn [] (stest/unstrument 'ivr.models.node.dtmf-catch-speak))})

(deftest dtmf-catch-speak-model
	(testing "speak action var"


		(testing "normal speech"
			(is (= [{:text "key bitch", :voice "alice"}]
						 (dc-speak/speak-action-var
							 {"toto" "key bitch"}
							 {:type :ivr.node.dtmf-catch/speak
								"varname" "toto"
								"voice" "alice"
								"pronounce" "normal"})))
			(is (= []
						 (dc-speak/speak-action-var
							 {"other" "key bitch"}
							 {:type :ivr.node.dtmf-catch/speak
								"varname" "toto"
								"voice" "alice"
								"pronounce" "normal"}))))


    (testing "phone number"
			(is (= [{:text "04." :voice "alice"}
							{:text "78." :voice "alice"}
							{:text "59." :voice "alice"}
							{:text "71." :voice "alice"}
							{:text "06." :voice "alice"}]
						 (dc-speak/speak-action-var
							 {"toto" "0478597106"}
							 {:type :ivr.node.dtmf-catch/speak
								"varname" "toto"
								"voice" "alice"
								"pronounce" "phone"})))
			(is (= [{:text "04." :voice "alice"}
							{:text "78." :voice "alice"}
							{:text "59." :voice "alice"}
							{:text "71." :voice "alice"}
							{:text "06." :voice "alice"}]
						 (dc-speak/speak-action-var
							 {"toto" "+33478597106"}
							 {:type :ivr.node.dtmf-catch/speak
								"varname" "toto"
								"voice" "alice"
								"pronounce" "phone"})))
			(is (= [{:text "1." :voice "alice"}
							{:text "2." :voice "alice"}
							{:text "3." :voice "alice"}
							{:text "4." :voice "alice"}
							{:text "5." :voice "alice"}]
						 (dc-speak/speak-action-var
							 {"toto" "12345"}
							 {:type :ivr.node.dtmf-catch/speak
								"varname" "toto"
								"voice" "alice"
								"pronounce" "phone"})))
			(is (= [{:text "+." :voice "alice"}
							{:text "3." :voice "alice"}
							{:text "3." :voice "alice"}
							{:text "1." :voice "alice"}
							{:text "2." :voice "alice"}
							{:text "3." :voice "alice"}
							{:text "4." :voice "alice"}
							{:text "5." :voice "alice"}
							{:text "6." :voice "alice"}
							{:text "7." :voice "alice"}]
						 (dc-speak/speak-action-var
							 {"toto" "+331234567"}
							 {:type :ivr.node.dtmf-catch/speak
								"varname" "toto"
								"voice" "alice"
								"pronounce" "phone"})))
			(is (= []
						 (dc-speak/speak-action-var
							 {"other" "0478597106"}
							 {:type :ivr.node.dtmf-catch/speak
								"varname" "toto"
								"voice" "alice"
								"pronounce" "phone"}))))))
