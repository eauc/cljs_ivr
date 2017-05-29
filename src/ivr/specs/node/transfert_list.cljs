(ns ivr.specs.node.transfert-list
  (:require [cljs.spec :as spec]
            [ivr.specs.node]))

(spec/def :ivr.node.transfert-list/dest
  string?)

(spec/def :ivr.node.transfert-list/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.transfert-list/dest])))
