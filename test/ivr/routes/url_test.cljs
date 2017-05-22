(ns ivr.routes.url-test
  (:require [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [cljs.spec.test :as stest]
            [ivr.routes.url :as url]))

(deftest url-service
  (testing "describe"
    (is (= "/smartccivr/config/"
           (url/describe [:v1 :config :explain])))
    (is (= {:link "/smartccivr/config"
            :explain "/smartccivr/config/"}
           (url/describe [:v1 :config])))
    (is (= {:link "/smartccivr/script/:script_id"
            :script-start "/smartccivr/script/:script_id/node/start"
            :script-enter-node "/smartccivr/script/:script_id/node/:node_id"
            :script-leave-node "/smartccivr/script/:script_id/node/:node_id/callback"}
           (url/describe [:v1 :action])))
    (is (= "/smartccivr/script/:script_id/node/start"
           (url/describe [:v1 :action :script-start]))))
  (testing "absolute"
    (testing "raw"
      (is (= "/smartccivr"
             (url/absolute [:v1])))
      (is (= "/smartccivr/config"
             (url/absolute [:v1 :config])))
      (is (= "/smartccivr/script/:script_id"
             (url/absolute [:v1 :action])))
      (is (= "/smartccivr/config/"
             (url/absolute [:v1 :config :explain])))
      (is (= "/smartccivr/script/:script_id/node/start"
             (url/absolute [:v1 :action :script-start])))
      (is (= "/smartccivr/script/:script_id/node/:node_id/callback"
             (url/absolute [:v1 :action :script-leave-node])))
      (is (= "/smartccivr/config"
             (url/absolute [:v1 :config :link]))))
    (testing "params interpolation"
      (is (= "/smartccivr/script/1234/node/42"
             (url/absolute [:v1 :action :script-enter-node] {:script-id "1234"
                                                             :node-id "42"})))))
  (testing "replace-params"
    (is (= "/script_id/1234/node_id/42/1234"
           (url/replace-params "/script_id/:script_id/node_id/:node_id/:script_id"
                               {:script-id "1234"
                                :node-id "42"})))))
