(ns ivr.models.verb-base
  (:require [cljs.spec :as spec]
            [ivr.specs.verb]
            [ivr.services.routes :as routes]))

(spec/fdef create-type
           :args (spec/cat :verb :ivr.verb/verb))
(defmulti create-type :type)


(defmethod create-type :default
  [params]
  [:Invalid params])
