(ns ivr.specs.script
  (:require [cljs.spec :as spec]
            [ivr.specs.call]
            [ivr.specs.node.announcement]))

(spec/def :ivr.script/account-id
  :ivr.call/id)

(spec/def :ivr.script/id
  string?)

(spec/def :ivr.script/start
  keyword?)

(spec/def :ivr.script/node
  (spec/or :announcement :ivr.node.annoucement/node))

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
