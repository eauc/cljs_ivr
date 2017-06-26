(ns ivr.core-test
  (:require [clojure.test :as test]
            ivr.models.acd-test
            ivr.models.call-test
            ivr.models.call-action-test
            ivr.models.call-number-test
            ivr.models.call-state-test
            ivr.models.cloudmemory-test
            ivr.models.ivrservices-test
            ivr.models.node-test
            ivr.models.node-action-test
            ivr.models.node.announcement-test
            ivr.models.node.dtmf-catch-test
            ivr.models.node.dtmf-catch-speak-test
            ivr.models.node.fetch-test
            ivr.models.node.route-test
            ivr.models.node.smtp-test
            ivr.models.node.transfert-list-test
            ivr.models.node.transfert-queue-test
            ivr.models.node.transfert-sda-test
            ivr.models.node.voice-record-test
            ivr.models.script-test
            ivr.models.store-test
            ivr.models.verbs-test
            ivr.routes.url-test
            ivr.services.calls.action-test
            ivr.services.calls.resolve-test
            ivr.services.calls.state-test
            ivr.services.calls.status-test
            ivr.services.config-test
            ivr.services.config.base-test
            ivr.services.routes-test
            pjstadig.humane-test-output))

(enable-console-print!)

(defn run-tests []
  (test/run-tests 'ivr.models.acd-test
                  'ivr.models.call-test
                  'ivr.models.call-action-test
                  'ivr.models.call-number-test
                  'ivr.models.call-state-test
                  'ivr.models.cloudmemory-test
                  'ivr.models.ivrservices-test
                  'ivr.models.node-test
                  'ivr.models.node-action-test
                  'ivr.models.node.announcement-test
                  'ivr.models.node.dtmf-catch-test
                  'ivr.models.node.dtmf-catch-speak-test
                  'ivr.models.node.fetch-test
                  'ivr.models.node.route-test
                  'ivr.models.node.smtp-test
                  'ivr.models.node.transfert-list-test
                  'ivr.models.node.transfert-queue-test
                  'ivr.models.node.transfert-sda-test
                  'ivr.models.node.voice-record-test
                  'ivr.models.script-test
                  'ivr.models.store-test
                  'ivr.models.verbs-test
                  'ivr.routes.url-test
                  'ivr.services.calls.action-test
                  'ivr.services.calls.resolve-test
                  'ivr.services.calls.state-test
                  'ivr.services.calls.status-test
                  'ivr.services.config.base-test
                  'ivr.services.config-test
                  'ivr.services.routes-test
                  ))

(defn -main []
  (run-tests))

(set! *main-cli-fn* -main)
