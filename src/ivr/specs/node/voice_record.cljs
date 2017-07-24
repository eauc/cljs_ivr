(ns ivr.specs.node.voice-record
  (:require [cljs.spec.alpha :as spec]
            [ivr.specs.node]))

(spec/def :ivr.node.voice-record/varname
  keyword?)

(spec/def :ivr.node.voice-record/validateKey
  string?)

(spec/def :ivr.node.voice-record/cancelKey
  string?)

(spec/def :ivr.node.voice-record.case/cancel
  string?)

(spec/def :ivr.node.voice-record.case/validate
  (spec/keys :opt-un [:ivr.node/next
                      :ivr.node/set]))

(spec/def :ivr.node.voice-record/cancelKey
  (spec/keys :opt-un [:ivr.node.voice-record.case/cancel
                      :ivr.node.voice-record.case/validate]))

(spec/def :ivr.node.voice-record/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.voice-record/varname]
                       :opt-un [:ivr.node.voice-record/validateKey
                                :ivr.node.voice-record/cancelKey
                                :ivr.node.voice-record/case])))
