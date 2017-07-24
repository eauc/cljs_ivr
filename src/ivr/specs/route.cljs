(ns ivr.specs.route
  (:require [cljs.spec.alpha :as spec]))

(spec/def :ivr.route/req
  any?)

(spec/def :ivr.route/res
  any?)

(spec/def :ivr.route/next
  fn?)

(spec/def :ivr.route/params
  map?)

(spec/def :ivr.route/route
  (spec/keys :req-un [:ivr.route/req
                      :ivr.route/res
                      :ivr.route/next]
             :opt-un [:ivr.route/params]))
