(ns ivr.specs.node.transfert-queue
  (:require [cljs.spec.alpha :as spec]
            [ivr.specs.node]))

(spec/def :ivr.node.transfert-queue/queue
  string?)

(spec/def :ivr.node.transfert-queue/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.transfert-queue/queue])))
