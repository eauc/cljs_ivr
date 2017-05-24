(ns ivr.models.verb-base
  (:require [cljs.spec :as spec]
            [ivr.specs.verb]))

(spec/fdef create-type
           :args (spec/cat :verb :ivr.verb/verb))
(defmulti create-type :type)
