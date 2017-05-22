(ns ivr.core-test
  (:require [clojure.test :as test]
            [pjstadig.humane-test-output]
            [ivr.models.node-test]
            [ivr.models.node.announcement-test]
            [ivr.models.script-test]
            [ivr.models.store-test]
            [ivr.models.verb-base-test]
            [ivr.routes.url-test]
            [ivr.services.config.base-test]
            [ivr.services.config-test]
            [ivr.services.routes-test]))

(enable-console-print!)

(defn run-tests []
  (test/run-tests 'ivr.models.node-test
                  'ivr.models.node.announcement-test
                  'ivr.models.script-test
                  'ivr.models.store-test
                  'ivr.models.verb-base-test
                  'ivr.routes.url-test
                  'ivr.services.config.base-test
                  'ivr.services.config-test
                  'ivr.services.routes-test))

(defn -main []
  (run-tests))

(set! *main-cli-fn* -main)
