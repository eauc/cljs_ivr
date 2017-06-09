(ns ivr.models.call
  (:require [cljs.spec :as spec]
            [ivr.specs.call]))

(spec/fdef info->call
           :args (spec/cat :info :ivr.call/info)
           :ret :ivr.call/call)
(defn info->call [info]
  {:info (select-keys info [:id :account-id :script-id :time])
   :action-data {}})
