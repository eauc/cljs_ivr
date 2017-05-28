(ns ivr.core-test
  (:require [clojure.test :as test]
            ivr.models.node-test
            ivr.models.node.announcement-test
            ivr.models.node.dtmf-catch-test
            ivr.models.node.dtmf-catch-speak-test
            ivr.models.node.fetch-test
            ivr.models.node.route-test
            ivr.models.node.smtp-test
            ivr.models.script-test
            ivr.models.store-test
            ivr.models.verbs-test
            ivr.routes.url-test
            ivr.services.calls-test
            ivr.services.config-test
            ivr.services.config.base-test
            ivr.services.routes-test
            pjstadig.humane-test-output))

(enable-console-print!)

(defn run-tests []
  (test/run-tests 'ivr.models.node-test
                  'ivr.models.node.announcement-test
                  'ivr.models.node.dtmf-catch-test
                  'ivr.models.node.dtmf-catch-speak-test
                  'ivr.models.node.fetch-test
                  'ivr.models.node.route-test
                  'ivr.models.node.smtp-test
                  'ivr.models.script-test
                  'ivr.models.store-test
                  'ivr.models.verbs-test
                  'ivr.routes.url-test
                  'ivr.services.calls-test
                  'ivr.services.config.base-test
                  'ivr.services.config-test
                  'ivr.services.routes-test
                  ))

(defn -main []
  (run-tests))

(set! *main-cli-fn* -main)
