(ns ivr.core-test
  (:require [clojure.test :as test]
            [ivr.models.node-test]
            [ivr.models.script-test]
            [ivr.models.store-test]
            [ivr.routes.url-test]
            [ivr.services.config.base-test]
            [ivr.services.config-test]
            [ivr.services.routes-test]
            [ivr.core :as core]))

(enable-console-print!)

(defn create-app []
  (test/run-tests 'ivr.models.node-test
                  'ivr.models.script-test
                  'ivr.models.store-test
                  'ivr.routes.url-test
                  'ivr.services.config.base-test
                  'ivr.services.config-test
                  'ivr.services.routes-test)
  (core/create-app))
