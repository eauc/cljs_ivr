(ns ivr.services.config-test
  (:require [cljs.core.async :as async :refer [<!]]
            [clojure.test :as test :refer-macros [deftest is run-tests testing async]]
            [ivr.services.config :as config])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest init
  (let [init-result
        (-> {:layers [{:path "http://localhost:3000"}
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
                                :apis {:v1 "/v1"}}
                       :loads [{:desc "http://localhost:3000"
                                :config {:module "IVR"
                                         :version "1.0.0"
                                         :apis {:v1 "/v1"}}}
                               {:desc "test/data/config.json"
                                :config {:key1 "file1"
                                         :key2 "file2"
                                         :file {:key1 "value1"
                                                :key2 "value2"}}}
                               {:desc "env"
                                :config {:port 3000}}]}
                      result)))
             :on-error #(is (= nil %))}
            (config/init))]
    (async
     done
     (go
       (<! init-result)
       (done)))))

;; (deftest init-error
;;   (async
;;    done
;;    (-> {:layers [{:path "unknown.json"}]
;;         :http-retry-timeout-s 1
;;         :http-retry-delay-s 1
;;         :on-success (fn [result]
;;                       (is (= nil result))
;;                       (done))
;;         :on-error (fn [error]
;;                     (is (= {:config {}
;;                             :loads [{:desc "unknown.json"
;;                                      :config {}
;;                                      :error (str "Cannot find module '" (.cwd js/process) "/unknown.json'")}]}
;;                             error))
;;                     (done))}
;;        (config/init))))
