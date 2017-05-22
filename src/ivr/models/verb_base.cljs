(ns ivr.models.verb-base
  (:require [cljs.spec :as spec]))

(spec/def ::type
  keyword?)

(spec/def ::verb
  (spec/keys :req-un [::type]))

(spec/fdef create-type
           :args (spec/cat :verb ::verb))
(defmulti create-type :type)
