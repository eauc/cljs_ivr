(ns ivr.specs.node.transfert-sda
  (:require [cljs.spec :as spec]))

(spec/def :ivr.node.transfert-sda/dest
  string?)

(spec/def :ivr.node.transfert-sda/case
  (spec/keys :opt-un [:ivr.node.transfert-sda/busy
                      :ivr.node.transfert-sda/no-answer
                      :ivr.node.transfert-sda/other]))

(spec/def :ivr.node.transfert-sda/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.transfert-sda/dest]
                       :opt-un [:ivr.node.transfert-sda/case])))
