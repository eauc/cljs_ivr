(ns ivr.models.node.voice-record-test
  (:require [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.models.node :as node]
            [ivr.models.node.voice-record :as vr-node]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.models.node.voice-record))
   :after (fn [] (stest/unstrument 'ivr.models.node.voice-record))})

(deftest voice-record-node-model
  (testing "conform"
    (is (= {:type "voicerecord"
            :varname nil}
           (node/conform-type {:type "voicerecord"})))
    (is (= {:type "voicerecord"
            :varname :to_record
            :case {:cancel nil}}
           (node/conform-type {:type "voicerecord"
                               :varname "to_record"
                               :case {}})))
    (is (= {:type "voicerecord"
            :varname :to_record
            :case {:cancel :42
                   :validate {:next :43}}}
           (node/conform-type {:type "voicerecord"
                               :varname "to_record"
                               :case {:cancel "42"
                                      :validate "43"}})))
    (is (= {:type "voicerecord"
            :varname :to_record
            :case {:validate {:next :43
                              :set {:type :ivr.node.preset/set
                                    :to :to_var
                                    :value "value"}}
                   :cancel nil}}
           (node/conform-type {:type "voicerecord"
                               :varname "to_record"
                               :case {:validate {:next "43"
                                                 :set {:varname "to_var"
                                                       :value "value"}}}})))
    (is (= {:type "voicerecord"
            :varname :to_record
            :case {:validate {:next nil
                              :set {:type :ivr.node.preset/copy
                                    :to :to_var
                                    :from :from_var}}
                   :cancel nil}}
           (node/conform-type {:type "voicerecord"
                               :varname "to_record"
                               :case {:validate {:set {:varname "to_var"
                                                       :value "$from_var"}}}}))))
  (testing "enter"
    (is (= {:dispatch
            [:ivr.models.node.voice-record/record-with-config
             {:node {:type "voicerecord"}
              :options {:options "options"}}]}
           (node/enter-type {:type "voicerecord"} {:options "options"})))
    (let [node {:type "voicerecord"
                :id "node-id"
                :script-id "script-id"
                :validateKey "4"
                :cancelKey "5"}
          verbs (fn [vs] {:verbs :create :data vs})
          options {:verbs verbs}
          config {:maxlength 245}]
      (testing "record-with-config"
        (is (= {:ivr.routes/response
                {:verbs :create
                 :data [{:type :ivr.verbs/record
                         :maxlength 245
                         :finishonkey "45"
                         :callbackurl "/smartccivr/script/script-id/node/node-id/callback"}]}}
               (vr-node/record-with-config
                {:config config}
                [:event {:node node :options options}]))))))
  (testing "leave"
    (let [verbs (fn [vs] {:verbs :create :data vs})
          options {:verbs verbs}
          node {:type "voicerecord"
                :id "node-id"
                :script-id "script-id"
                :finishKey "4"
                :cancelKey "5"
                :varname :record_var}]
      (testing "cancel"
        (let [params {:record_cause "digit-a" :record_digits "435"}
              options (merge options {:params params})]
          (is (= {:ivr.routes/response
                  {:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
                 (node/leave-type node options)))
          (let [node (merge node {:case {:cancel :42}})]
            (is (= {:ivr.routes/response
                    {:verbs :create
                     :data [{:type :ivr.verbs/redirect
                             :path "/smartccivr/script/script-id/node/42"}]}}
                   (node/leave-type node options))))))
      (testing "validate"
        (let [params {:record_cause "digit-a"
                      :record_digits "53"
                      :record_url "/record/url"}
              action-data {:action :data}
              options (merge options {:action-data action-data
                                      :call-id "call-id"
                                      :params params})]
          (is (= {:ivr.call/action-data
                  {:call-id "call-id"
                   :data {:action :data
                          :record_var "/record/url"}}
                  :ivr.routes/response
                  {:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
                 (node/leave-type node options))))
        (let [params {:record_cause "hangup"
                      :record_digits "435"
                      :record_url "/record/url"}
              action-data {:action :data}
              options (merge options {:action-data action-data
                                      :call-id "call-id"
                                      :params params})]
          (let [node (merge node {:case {:validate {:next :44
                                                    :set {:type :ivr.node.preset/copy
                                                          :from :record_var
                                                          :to :to_var}}}})]
            (is (= {:ivr.call/action-data
                    {:call-id "call-id"
                     :data {:action :data
                            :record_var "/record/url"
                            :to_var "/record/url"}}
                    :ivr.routes/response
                    {:verbs :create, :data [{:type :ivr.verbs/redirect
                                             :path "/smartccivr/script/script-id/node/44"}]}}
                   (node/leave-type node options))))
          (let [node (merge node {:case {:validate {:set {:type :ivr.node.preset/set
                                                          :value "set_value"
                                                          :to :to_var}}}})]
            (is (= {:ivr.call/action-data
                    {:call-id "call-id"
                     :data {:action :data
                            :record_var "/record/url"
                            :to_var "set_value"}}
                    :ivr.routes/response
                    {:verbs :create, :data [{:type :ivr.verbs/hangup}]}}
                   (node/leave-type node options)))))))))
