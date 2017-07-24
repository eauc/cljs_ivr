(ns ivr.specs.db
  (:require [cljs.spec.alpha :as spec]
            [ivr.specs.call]
            [ivr.specs.config]))

(spec/def :ivr.db/config-info
  :ivr.config/info)

(spec/def :ivr.db/calls
  :ivr.call/store)

(spec/def :ivr.db/db
  (spec/keys :req-un [:ivr.db/config-info
                      :ivr.db/calls]))
