(ns ivr.core-test
  (:require [clojure.test :as test]
            [ivr.services.config.base-test]
            [ivr.services.config-test]
            [ivr.core :as core]))

(enable-console-print!)

(defn create-app []
  (test/run-tests 'ivr.services.config.base-test
                  'ivr.services.config-test)
  (core/create-app))
