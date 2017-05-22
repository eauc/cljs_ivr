(ns ivr.models.verb-base-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.verb-base :as verb-base]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.verb-base))
   :after (fn [] (stest/unstrument 'ivr.models.verb-base))})

(deftest verb-base-model
  (testing "hangup"
    (is (= [:HangUp]
           (verb-base/create-type {:type :ivr.verbs/hangup}))))
  (testing "gather"
    (is (= [:Gather {:callbackmethod "POST"} '()]
           (verb-base/create-type {:type :ivr.verbs/gather})))
    (is (= [:Gather {:finishonkey "key"
                     :numdigits 4
                     :timeout 42
                     :callbackurl "url"
                     :callbackmethod "PUT"} '()]
           (verb-base/create-type {:type :ivr.verbs/gather
                                   :finishonkey "key"
                                   :numdigits 4
                                   :timeout 42
                                   :callbackurl "url"
                                   :callbackmethod "PUT"
                                   :ignored "whatever"})))
    (is (= [:Gather {:callbackmethod "POST"} '([:Play "sound"]
                                               [:Speak {:locutor "alice"} "text"])]
           (verb-base/create-type {:type :ivr.verbs/gather
                                   :play ["sound"
                                          {:voice "alice" :text "text"}]}))))
  (testing "play"
    (is (= [:Play "sound-path"]
           (verb-base/create-type {:type :ivr.verbs/play
                                   :path "sound-path"}))))
  (testing "redirect"
    (is (= [:Redirect "path"]
           (verb-base/create-type {:type :ivr.verbs/redirect
                                   :path "path"})))))
