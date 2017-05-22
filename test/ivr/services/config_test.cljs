(ns ivr.services.config-test
  (:require [cljs.core.async :as async :refer [<!]]
            [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.services.config :as config])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.services.config))
   :after (fn [] (stest/unstrument 'ivr.services.config))})

(deftest config-service
  (testing "init"
    (let [init-result
          (config/init
           {:layers [{:path "http://localhost:3000"}
                     {:path "test/data/config.json"}
                     {:desc "env" :config {:port 3000}}]
            :http-retry-timeout-s 1
            :http-retry-delay-s 1
            :on-success
            (fn [result]
              (is (= {:config {:port 3000
                               :key1 "file1"
                               :key2 "file2"
                               :file {:key1 "value1"
                                      :key2 "value2"}
                               :module "IVR"
                               :version "1.0.0"
                               :apis {:v1 {:link "/smartccivr"}}}
                      :loads [{:desc "http://localhost:3000"
                               :config {:module "IVR"
                                        :version "1.0.0"
                                        :apis {:v1 {:link "/smartccivr"}}}}
                              {:desc "test/data/config.json"
                               :config {:key1 "file1"
                                        :key2 "file2"
                                        :file {:key1 "value1"
                                               :key2 "value2"}}}
                              {:desc "env"
                               :config {:port 3000}}]}
                     result)))
            :on-error #(is (= nil %))})
          init-error-result
          (config/init
           {:layers [{:path "unknown.json"}]
            :http-retry-timeout-s 1
            :http-retry-delay-s 1
            :on-success (fn [result]
                          (is (= nil result)))
            :on-error (fn [error]
                        (is (= {:config {}
                                :loads [{:desc "unknown.json"
                                         :config {}
                                         :error (str "Cannot find module '"
                                                     (.cwd js/process)
                                                     "/unknown.json'")}]}
                               error)))})]
      (async
       done
       (go
         (<! init-result)
         (<! init-error-result)
         (done)))))

  (testing "explain"
    (testing "empty config"
      (let [config-info {:config {}
                         :loads []}]
        (is (= config-info
               (config/explain config-info {})))
        (is (= config-info
               (config/explain config-info {:path "to.value"})))))
    (let [config-info {:config {:port 3000
                                :dispatch_url {:internal "http://clouddispatch"}}
                       :loads [{:desc "local.json"
                                :config {:dispatch_url {:internal "http://localhost:8080"}}}
                               {:desc "http://cloudstore"
                                :config {:port 3000
                                         :dispatch_url {:internal "http://clouddispatch"}}}
                               {:desc "env"
                                :config {:port 3000}}]}]
      (testing "empty path"
        (is (= config-info
               (config/explain config-info {}))))
      (testing "simple path"
        (is (= {:config {:port 3000}
                :loads [{:desc "local.json"
                         :config {}}
                        {:desc "http://cloudstore"
                         :config {:port 3000}}
                        {:desc "env"
                         :config {:port 3000}}]}
               (config/explain config-info {:path "port"}))))
      (testing "deep path"
        (is (= {:config {:dispatch_url {:internal "http://clouddispatch"}}
                :loads [{:desc "local.json"
                         :config {:dispatch_url {:internal "http://localhost:8080"}}}
                        {:desc "http://cloudstore"
                         :config {:dispatch_url {:internal "http://clouddispatch"}}}
                        {:desc "env"
                         :config {}}]}
               (config/explain config-info {:path "dispatch_url.internal"}))))
      (testing "unknown path"
        (is (= {:config {}
                :loads [{:desc "local.json"
                         :config {}}
                        {:desc "http://cloudstore"
                         :config {}}
                        {:desc "env"
                         :config {}}]}
               (config/explain config-info {:path "unknown"})))
        (is (= {:config {}
                :loads [{:desc "local.json"
                         :config {}}
                        {:desc "http://cloudstore"
                         :config {}}
                        {:desc "env"
                         :config {}}]}
               (config/explain config-info {:path "to.unknown"})))))))
