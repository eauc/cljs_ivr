(ns ivr.models.verbs-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.verbs :as verbs]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.verbs))
   :after (fn [] (stest/unstrument 'ivr.models.verbs))})

(deftest verbs-model
  (testing "unknown"
    (is (= [:Response `([:Invalid {:type :ivr.verbs/unknown :params :values}])]
           (:data
            (verbs/create [{:type :ivr.verbs/unknown :params :values}])))))


  (testing "hangup"
    (is (= [:Response `([:HangUp])]
           (:data
            (verbs/create [{:type :ivr.verbs/hangup}])))))


  (testing "gather"
    (is (= [:Response `([:Gather {:callbackmethod "POST"} ()])]
           (:data
            (verbs/create [{:type :ivr.verbs/gather}]))))
    (is (= [:Response `([:Gather {:finishonkey "key"
                                  :numdigits 4
                                  :timeout 42
                                  :callbackurl "url"
                                  :callbackmethod "PUT"} ()])]
           (:data
            (verbs/create [{:type :ivr.verbs/gather
                            :finishonkey "key"
                            :numdigits 4
                            :timeout 42
                            :callbackurl "url"
                            :callbackmethod "PUT"
                            :ignored "whatever"}]))))
    (is (= [:Response `([:Gather {:callbackmethod "POST"}
                         ([:Play "sound"]
                          [:Speak {:locutor "alice"} "text"])])]
           (:data
            (verbs/create [{:type :ivr.verbs/gather
                            :play ["sound"
                                   {:voice "alice" :text "text"}]}])))))


  (testing "play"
    (is (= [:Response `([:Play "sound-path"])]
           (:data
            (verbs/create [{:type :ivr.verbs/play
                            :path "sound-path"}])))))


  (testing "redirect"
    (is (= [:Response `([:Redirect "path"])]
           (:data
            (verbs/create [{:type :ivr.verbs/redirect
                            :path "path"}])))))

  (testing "first-verb"
    (is (= nil
           (verbs/first-verb (verbs/create []))))
    (is (= :HangUp
           (verbs/first-verb (verbs/create [{:type :ivr.verbs/hangup}]))))
    (is (= :Dial
           (verbs/first-verb (verbs/create [{:type :ivr.verbs/dial-number}
                                            {:type :ivr.verbs/hangup}]))))))
