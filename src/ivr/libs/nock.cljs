(ns ivr.libs.nock
  (:require [cljs.nodejs :as nodejs]
            [ivr.debug :refer [debug?]]
            [ivr.libs.logger :as logger]))

(defonce nock (nodejs/require "nock"))

(def log
  (logger/create "nock"))

(defonce cloudstore
  (when debug?
    (-> (nock "http://clouddispatch/cloudstore")
        (.log js/console.log)
        (.persist)
        (.get "/account/0007/script/1234")
        (.reply 200 (clj->js {:id "1234"
                              :start "1"
                              :nodes {:1 {:type "announcement"
                                          :soundname "son1"
                                          :preset {:value "toto"
                                                   :varname "titi"}}
                                      :2 {:type "dtmfcatch"
                                          :finishonkey "4"
                                          :max_attempts 3
                                          :numdigits 3
                                          :preset {:value "+33478597106"
                                                   :varname "toto"}
                                          :retry 2
                                          :timeout 5
                                          :validationpattern "[421]"
                                          :varname "dtmfcatch"
                                          :welcome [{:varname "titi"
                                                     :voice "alice"}
                                                    {:soundname "son1"}
                                                    {:varname "toto"
                                                     :voice "alice"
                                                     :pronounce "phone"}]
                                          :case {:dtmf_ok {:set {:value "dtmf_toto"
                                                                 :varname "dtmf_ok"}
                                                           :next "43"}
                                                 :max_attempt_reached "44"}}
                                      :3 {:type "fetch"
                                          :varname "to_fetch"
                                          :id_routing_rule "71"
                                          :next "2"}
                                      :4 {:type "route"
                                          :varname "titi"
                                          :case {:toto {:next "3"
                                                        :set {:varname "to_route"
                                                              :value "toto"}}}}
                                      :5 {:type "smtp"
                                          :subject "hello world"
                                          :to "manu"
                                          :attachment "/attach.txt"
                                          :text "youpi"
                                          :next "4"}
                                      :6 {:type "transferlist"
                                          :dest "3456"
                                          :failover "5"}
                                      :7 {:type "transferlist"
                                          :dest "unknown"
                                          :failover "6"}
                                      :8 {:type "transferqueue"
                                          :queue "3456"}
                                      :9 {:type "transferqueue"
                                          :queue "unknown"}
                                      :10 {:type "transfersda"
                                           :dest "dest-sda"
                                           :case {:noanswer "42"
                                                  :busy "71"
                                                  :other "3"}}
                                      :11 {:type "voicerecord"
                                           :varname "record"
                                           :validateKey "4"
                                           :cancelKey "#"
                                           :case {:cancel "4"
                                                  :validate "7"}}}}))
        (.get "/account/0007/file")
        (.query true)
        (.reply 200 (clj->js {:meta {:total_count 1}
                              :objects [{:_id "id_son1"}]}))
        (.get "/account/0007")
        (.reply 200 (clj->js {:fromSda "CALLER"
                              :ringingTimeoutSec 15
                              :ringing_tone "ringing"})))))


(defonce acdlink
  (when debug?
    (-> (nock "http://clouddispatch/smartccacdlink")
        (.log js/console.log)
        (.defaultReplyHeaders #js {:Content-Type "application/json"})
        (.persist)
        (.post "/call/2234/enqueue"
               (fn [body]
                 (js/console.log "acd enqueue" body)
                 (= "3456" (aget body "queue_id"))))
        (.reply 200 #js {:waitSound "queue_waiting"}))))


(defonce ivrservices
  (when debug?
    (-> (nock "http://clouddispatch/smartccivrservices")
        (.log js/console.log)
        (.defaultReplyHeaders #js {:Content-Type "application/json"})
        (.persist)
        (.post "/account/0007/routingrule/71/eval")
        (.reply 200 "\"rule_value\"")
        (.post "/account/0007/mail"
               (fn [body]
                 (js/console.log "mail info" body)
                 true))
        (.reply 201)
        (.post "/account/0007/destinationlist/3456/eval")
        (.reply 200 #js {:sda "list-sda"
                         :param1 "val1"
                         :param2 "val2"}))))
