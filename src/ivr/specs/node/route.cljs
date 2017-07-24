(ns ivr.specs.node.route
  (:require [cljs.spec.alpha :as spec]
            [ivr.specs.node]))

(spec/def :ivr.node.route/varname
  keyword?)

(spec/def :ivr.node.route/match
  (spec/keys :opt-un [:ivr.node/set
                      :ivr.node/next]))

(spec/def :ivr.node.route/case
  (spec/coll-of :ivr.node.route/match :kind map?))

(spec/def :ivr.node.route/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.route/varname]
                       :opt-un [:ivr.node.route/case])))
