(ns ivr.core-test
  (:require [clojure.test :as test]
            [ivr.services.config.base-test]
            [ivr.services.config-test]
            [ivr.core :as core]))

(enable-console-print!)

(defn create-app []
  (test/run-all-tests #"ivr.*-test")
  (core/create-app))
