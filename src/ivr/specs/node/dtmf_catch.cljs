(ns ivr.specs.node.dtmf-catch
  (:require [ivr.specs.node]
            [cljs.spec :as spec]))

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

(spec/def :ivr.node.dtmf-catch/welcome
  (spec/coll-of :ivr.node.dtmf-catch/sound :kind vector?))

(spec/def :ivr.node.dtmf-catch/retry
  integer?)

(spec/def :ivr.node.dtmf-catch/finishonkey
  (or integer?
      string?))

(spec/def :ivr.node.dtmf-catch/numdigits
  integer?)

(spec/def :ivr.node.dtmf-catch/timeout
  integer?)

(spec/def :ivr.node.dtmf-catch/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.dtmf-catch/welcome
                                :ivr.node.dtmf-catch/retry]
                       :opt-un [:ivr.node.dtmf-catch/finishonkey
                                :ivr.node.dtmf-catch/numdigits
                                :ivr.node.dtmf-catch/timeout])))

(spec/def :ivr.node.dtmf-catch/retries
  :ivr.node.dtmf-catch/retry)

(spec/def :ivr.node.dtmf-catch/options
  (spec/and :ivr.node/options
            (spec/keys :opt-un [:ivr.node.dtmf-catch/retries])))
