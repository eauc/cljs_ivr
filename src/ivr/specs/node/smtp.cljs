(ns ivr.specs.node.smtp
  (:require [cljs.spec :as spec]))

(spec/def :ivr.node.smtp/attachment
  string?)

(spec/def :ivr.node.smtp/subject
  string?)

(spec/def :ivr.node.smtp/text
  string?)

(spec/def :ivr.node.smtp/to
  string?)

(spec/def :ivr.node.smtp/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.smtp/subject
                                :ivr.node.smtp/to
                                :ivr.node.smtp/text]
                       :opt-un [:ivr.node.smtp/attachment])))
