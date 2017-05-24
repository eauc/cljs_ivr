(ns ivr.specs.store
  (:require [cljs.spec :as spec]))

(spec/def :ivr.store.query/type
  keyword?)

(spec/def :ivr.store/query
  (spec/keys :req-un [:ivr.store.query/type]))
