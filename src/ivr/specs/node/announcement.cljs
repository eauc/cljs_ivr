(ns ivr.specs.node.announcement
  (:require [cljs.spec :as spec]
            [ivr.specs.node]))

(spec/def :ivr.node.annoucement/soundname
  string?)

(spec/def :ivr.node.annoucement/disabled
  boolean?)

(spec/def :ivr.node.annoucement/no_barge
  boolean?)

(spec/def :ivr.nodes.announcement.preset/type
  #{:ivr.nodes.announcement.preset/copy
    :ivr.nodes.announcement.preset/set})

(spec/def :ivr.nodes.announcement.preset/value
  string?)

(spec/def :ivr.nodes.announcement.preset/from
  string?)

(spec/def :ivr.nodes.announcement.preset/to
  string?)

(spec/def :ivr.node.annoucement/preset
  (spec/keys :req-un [:ivr.nodes.announcement.preset/type
                      :ivr.nodes.announcement.preset/to]
             :opt-un [:ivr.nodes.announcement.preset/from
                      :ivr.nodes.announcement.preset/value]))

(spec/def :ivr.node.annoucement/node
  (spec/and :ivr.models.node/node
            (spec/keys :req-un [:ivr.node.annoucement/soundname]
                       :opt-un [:ivr.node.annoucement/disabled
                                :ivr.node.annoucement/no_barge
                                :ivr.node.annoucement/preset])))
