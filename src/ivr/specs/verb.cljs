(ns ivr.specs.verb
  (:require [cljs.spec.alpha :as spec]))

(spec/def :ivr.verb/type
  keyword?)

(spec/def :ivr.verb/verb
  (spec/keys :req-un [:ivr.verb/type]))

(spec/def :ivr.verb/verbs
  (spec/coll-of :ivr.verb/verb :kind vector?))
