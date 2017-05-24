(ns ivr.specs.call
  (:require [cljs.spec :as spec]))

(spec/def :ivr.call/id
  string?)

(spec/def :ivr.call/account_id
  string?)

(spec/def :ivr.call/script_id
  string?)

(spec/def :ivr.call/info
  (spec/keys :req-un [:ivr.call/id
                      :ivr.call/account-id
                      :ivr.call/script-id]))

(spec/def :ivr.call/action-data
  map?)

(spec/def :ivr.call/call
  (spec/keys :req-un [:ivr.call/info]))

(spec/def :ivr.call/store
  (spec/map-of :ivr.call/id :ivr.call/call))
