(ns ivr.specs.script
  (:require [cljs.spec.alpha :as spec]
            [ivr.specs.call]
            [ivr.specs.node.announcement]
            [ivr.specs.node.dtmf-catch]
            [ivr.specs.node.fetch]
            [ivr.specs.node.route]
            [ivr.specs.node.smtp]
            [ivr.specs.node.transfert-list]
            [ivr.specs.node.transfert-queue]
            [ivr.specs.node.transfert-sda]
            [ivr.specs.node.voice-record]))

(spec/def :ivr.script/account-id
  :ivr.call/id)

(spec/def :ivr.script/id
  string?)

(spec/def :ivr.script/start
  keyword?)

(spec/def :ivr.script/node
  (spec/or :announcement :ivr.node.announcement/node
           :dtmf-catch :ivr.node.dtmf-catch/node
           :fetch :ivr.node.fetch/node
           :route :ivr.node.route/node
           :smtp :ivr.node.smtp/node
           :transfert-list :ivr.node.transfert-list/node
           :transfert-queue :ivr.node.transfert-queue/node
           :transfert-sda :ivr.node.transfert-sda/node
           :voice-record :ivr.node.voice-record/node))

(spec/def :ivr.script/nodes
  (spec/coll-of :ivr.script/node :kind map?))

(spec/def :ivr.script/script
  (spec/keys :req-un [:ivr.script/id
                      :ivr.script/account-id
                      :ivr.script/start
                      :ivr.script/nodes]))

(spec/def :ivr.script/call-id
  :ivr.call/id)

(spec/def :ivr.script/enter-node
  fn?)

(spec/def :ivr.script/leave-node
  fn?)

(spec/def :ivr.script/start-options
  (spec/keys :req-un [:ivr.call/action-data
                      :ivr.script/call-id
                      :ivr.script/enter-node]))

(spec/def :ivr.script/leave-node-options
  (spec/keys :req-un [:ivr.call/action-data
                      :ivr.script/call-id
                      :ivr.script/leave-node]))
