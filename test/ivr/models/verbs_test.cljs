(ns ivr.models.verbs-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.models.verbs :as verbs]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.verbs))
   :after (fn [] (stest/unstrument 'ivr.models.verbs))})

(deftest verbs-model
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
                            :path "path"}]))))))
