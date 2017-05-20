(ns ivr.services.config.base-test
  (:require [cljs.core.async :refer [<!]]
            [clojure.test :as test :refer-macros [deftest is run-tests testing async]]
            [ivr.services.config.base :as base])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest layer-type
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

(deftest load-layer
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
                 :config {:module "IVR" :version "1.0.0" :apis {:v1 "/v1"}}}
                (<! http-load)))
         (is (= {:desc "http://localhost:3001"
                :config {}
                :error "connect ECONNREFUSED 127.0.0.1:3001"}
                (<! http-error-load))))
       (done)))))
