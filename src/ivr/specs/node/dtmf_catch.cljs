(ns ivr.specs.node.dtmf-catch
  (:require [cljs.spec.alpha :as spec]
            [ivr.specs.node]))

(spec/def :ivr.node.dtmf-catch.sound/pronounce
  #{"normal" "phone"})

(spec/def :ivr.node.dtmf-catch.sound/soundname
  string?)

(spec/def :ivr.node.dtmf-catch.sound/text
  string?)

(spec/def :ivr.node.dtmf-catch.sound/varname
  string?)

(spec/def :ivr.node.dtmf-catch.sound/voice
  string?)

(spec/def :ivr.node.dtmf-catch.sound/file
  (spec/keys :req-un [:ivr.node.dtmf-catch.sound/type
                      :ivr.node.dtmf-catch.sound/soundname]))

(spec/def :ivr.node.dtmf-catch.sound/speak
  (spec/keys :req-un [:ivr.node.dtmf-catch.sound/type
                      :ivr.node.dtmf-catch.sound/varname
                      :ivr.node.dtmf-catch.sound/voice
                      :ivr.node.dtmf-catch.sound/pronounce]))

(spec/def :ivr.node.dtmf-catch.sound/play
  (spec/or :file string?
           :speak (spec/keys :req-un [:ivr.node.dtmf-catch.sound/text
                                      :ivr.node.dtmf-catch.sound/voice])))

(spec/def :ivr.node.dtmf-catch/sound
  (spec/or :file-sound :ivr.node.dtmf-catch.sound/file
           :speak-sound :ivr.node.dtmf-catch.sound/speak))

(spec/def :ivr.node.dtmf-catch/dtmf_ok
  (spec/keys :opt-un [:ivr.node/next
                      :ivr.node/set]))

(spec/def :ivr.node.dtmf-catch/max-attempt-reached
  :ivr.node/next)

(spec/def :ivr.node.dtmf-catch/case
  (spec/keys :opt-un [:ivr.node.dtmf-catch/dtmf_ok
                      :ivr.node.dtmf-catch/max_attempt_reached]))

(spec/def :ivr.node.dtmf-catch/finishonkey
  string?)

(spec/def :ivr.node.dtmf-catch/max_attempts
  integer?)

(spec/def :ivr.node.dtmf-catch/numdigits
  integer?)

(spec/def :ivr.node.dtmf-catch/retry
  integer?)

(spec/def :ivr.node.dtmf-catch/timeout
  integer?)

(spec/def :ivr.node.dtmf-catch/validationpattern
  string?)

(spec/def :ivr.node.dtmf-catch/varname
  string?)

(spec/def :ivr.node.dtmf-catch/welcome
  (spec/coll-of :ivr.node.dtmf-catch/sound :kind vector?))

(spec/def :ivr.node.dtmf-catch/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.dtmf-catch/max_attempts
                                :ivr.node.dtmf-catch/retry
                                :ivr.node.dtmf-catch/varname
                                :ivr.node.dtmf-catch/welcome]
                       :opt-un [:ivr.node.dtmf-catch/case
                                :ivr.node.dtmf-catch/finishonkey
                                :ivr.node.dtmf-catch/numdigits
                                :ivr.node.dtmf-catch/timeout
                                :ivr.node.dtmf-catch/validationpattern])))

(spec/def :ivr.node.dtmf-catch/retries
  :ivr.node.dtmf-catch/retry)

(spec/def :ivr.node.dtmf-catch/digit
  string?)

(spec/def :ivr.node.dtmf-catch/digits
  (spec/coll-of :ivr.node.dtmf-catch/digit :kind vector?))

(spec/def :ivr.node.dtmf-catch/options
  (spec/and :ivr.node/options
            (spec/keys :opt-un [:ivr.node.dtmf-catch/retries])))
