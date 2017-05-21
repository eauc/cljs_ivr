(ns ivr.services.config.base-test
  (:require [cljs.core.async :refer [<!]]
            [cljs.spec.test :as stest]
            [clojure.test :as test :refer-macros [async deftest is run-tests testing use-fixtures]]
            [ivr.services.config.base :as base])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(use-fixtures :once
  {:before (fn [] (stest/instrument 'ivr.services.config.base))
   :after (fn [] (stest/unstrument 'ivr.services.config.base))})

(deftest config-base-service
  (testing "layer-type"
    (is (= :file-layer
           (base/layer-type
            {:path "test/data/config.json"} {})))
    (is (= :http-layer
           (base/layer-type
            {:path "http://localhost:3000"} {})))
    (is (= :object-layer
           (base/layer-type
            {:desc "env" :config {}} {})))
    (is (= :invalid-layer
           (base/layer-type
            {:desc "env" :config []} {})))
    (is (= :invalid-layer
           (base/layer-type
            {:desc "env" :config "toto"} {})))
    (is (= :invalid-layer
           (base/layer-type
            {:config {}} {})))
    (is (= :invalid-layer
           (base/layer-type
            {} {})))
    (is (= :invalid-layer
           (base/layer-type
            "toto" {}))))

  (testing "load-layer"
    (let [object-load (base/load-layer {:desc "env" :config {:port 3000}} {})
          file-load (base/load-layer {:path "test/data/config.json"} {})
          file-error-load (base/load-layer {:path "unknown.json"} {})
          http-load (base/load-layer {:path "http://localhost:3000"}
                                     {:http-retry-timeout-s 1
                                      :http-retry-delay-s 1})
          http-error-load (base/load-layer {:path "http://localhost:3001"}
                                           {:http-retry-timeout-s 1
                                            :http-retry-delay-s 1})]
      (async
       done
       (go
         (testing "object"
           (is (= {:desc "env" :config {:port 3000}}
                  (<! object-load))))
         (testing "file"
           (is (= {:desc "test/data/config.json"
                   :config {:key1 "file1"
                            :key2 "file2"
                            :file {:key1 "value1"
                                   :key2 "value2"}}}
                  (<! file-load)))
           (is (= {:desc "unknown.json"
                   :config {}
                   :error (str "Cannot find module '" (.cwd js/process) "/unknown.json'")}
                  (<! file-error-load))))
         (testing "http"
           (is (= {:desc "http://localhost:3000"
                   :config {:module "IVR" :version "1.0.0" :apis {:v1 "/smartccivr"}}}
                  (<! http-load)))
           (is (= {:desc "http://localhost:3001"
                   :config {}
                   :error "connect ECONNREFUSED 127.0.0.1:3001"}
                  (<! http-error-load))))
         (done))))))
