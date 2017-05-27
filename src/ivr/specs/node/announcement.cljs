(ns ivr.specs.node.announcement
  (:require [cljs.spec :as spec]
            [ivr.specs.node]))

(spec/def :ivr.node.announcement/soundname
  string?)

(spec/def :ivr.node.announcement/disabled
  boolean?)

(spec/def :ivr.node.announcement/no_barge
  boolean?)

(spec/def :ivr.node.announcement/node
  (spec/and :ivr.node/node
            (spec/keys :req-un [:ivr.node.announcement/soundname]
                       :opt-un [:ivr.node.announcement/disabled
                                :ivr.node.announcement/no_barge
                                :ivr.node/preset])))
