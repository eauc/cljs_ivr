(ns ivr.specs.node.fetch
  (:require [cljs.spec :as spec]
            [ivr.specs.node]))

(spec/def :ivr.node.fetch/id_routing_rule
  string?)

(spec/def :ivr.node.fetch/varname
  keyword?)

(spec/def :ivr.node.fetch/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.fetch/varname
                                :ivr.node.fetch/id_routing_rule])))
