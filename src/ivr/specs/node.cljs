(ns ivr.specs.node
  (:require [cljs.spec :as spec]
            [ivr.specs.call]))

(def known-types
  #{"announcement"
    "dtmfcatch"})

(spec/def :ivr.node/script-id
  string?)

(spec/def :ivr.node/next
  keyword?)

(spec/def :ivr.node/type
  known-types)

(spec/def :ivr.nodes.preset/type
  #{:ivr.nodes.preset/copy
    :ivr.nodes.preset/set})

(spec/def :ivr.nodes.preset/value
  string?)

(spec/def :ivr.nodes.preset/from
  string?)

(spec/def :ivr.nodes.preset/to
  string?)

(spec/def :ivr.node/preset
  (spec/keys :req-un [:ivr.nodes.announcement.preset/type
                      :ivr.nodes.announcement.preset/to]
             :opt-un [:ivr.nodes.announcement.preset/from
                      :ivr.nodes.announcement.preset/value]))

(spec/def :ivr.node/node
  (spec/keys :req-un [:ivr.call/account-id
                      :ivr.node/script-id
                      :ivr.node/type]
             :opt-un [:ivr.node/next]))

(spec/def :ivr.node/call-id
  :ivr.call/id)

(spec/def :ivr.node/store
  fn?)

(spec/def :ivr.node/verbs
  fn?)

(spec/def :ivr.node/options
  (spec/keys :req-un [:ivr.models.call/action-data
                      :ivr.node/call-id]
             :opt-un [:ivr.node/store
                      :ivr.node/verbs]))
