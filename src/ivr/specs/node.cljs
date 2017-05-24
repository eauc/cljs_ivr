(ns ivr.specs.node
  (:require [cljs.spec :as spec]
            [ivr.specs.call]))

(def known-types
  #{"announcement"})

(spec/def :ivr.node/script-id
  string?)

(spec/def :ivr.node/next
  keyword?)

(spec/def :ivr.node/type
  known-types)

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

(spec/def :ivr.node/enter-options
  (spec/keys :req-un [:ivr.models.call/action-data
                      :ivr.node/call-id]
             :opt-un [:ivr.node/store
                      :ivr.node/verbs]))
