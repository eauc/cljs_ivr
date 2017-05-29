(ns ivr.specs.node
  (:require [cljs.spec :as spec]
            [ivr.specs.call]))

(def known-types
  #{"announcement"
    "dtmfcatch"
    "fetch"
    "route"
    "smtp"
    "transferlist"})

(spec/def :ivr.node/script-id
  string?)

(spec/def :ivr.node/next
  keyword?)

(spec/def :ivr.node/type
  known-types)

(spec/def :ivr.node.preset/type
  #{:ivr.node.preset/copy
    :ivr.node.preset/set})

(spec/def :ivr.node.preset/value
  string?)

(spec/def :ivr.node.preset/from
  keyword?)

(spec/def :ivr.node.preset/to
  keyword?)

(spec/def :ivr.node/preset
  (spec/keys :req-un [:ivr.node.preset/type
                      :ivr.node.preset/to]
             :opt-un [:ivr.node.preset/from
                      :ivr.node.preset/value]))

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

(spec/def :ivr.node/params
  map?)

(spec/def :ivr.node/options
  (spec/keys :req-un [:ivr.models.call/action-data
                      :ivr.node/call-id]
             :opt-un [:ivr.node/params
                      :ivr.node/store
                      :ivr.node/verbs]))
