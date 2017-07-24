(ns ivr.services.config.base
  (:require [cljs.spec.alpha :as spec]
            [ivr.specs.config]
            [ivr.libs.logger :as logger]))

(def log (logger/create "Config"))

(defn layer-type [layer _]
  (cond
    (spec/valid? :ivr.config/http-layer layer) :http-layer
    (spec/valid? :ivr.config/file-layer layer) :file-layer
    (spec/valid? :ivr.config/object-layer layer) :object-layer
    :else :invalid-layer))

(spec/fdef load-layer
           :args (spec/cat :layer :ivr.config/http-layer
                           :options :ivr.config.layer.load/options)
           :ret any?)
(defmulti load-layer layer-type)
