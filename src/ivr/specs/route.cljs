(ns ivr.specs.route
  (:require [cljs.spec :as spec]))

(spec/def :ivr.route/req
  any?)

(spec/def :ivr.route/res
  any?)

(spec/def :ivr.route/next
  fn?)

(spec/def :ivr.route/route
  (spec/keys :req-un [:ivr.route/req]
             :opt-un [:ivr.route/res
                      :ivr.route/next]))
