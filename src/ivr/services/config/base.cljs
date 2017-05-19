(ns ivr.services.config.base
  (:require [cljs.spec :as spec]
            [ivr.services.config.spec]
            [ivr.libs.logger :as logger]))

(def log (logger/create "Config"))

(spec/fdef load-layer
           :args (spec/cat :layer :ivr.config/http-layer
                           :options (spec/keys :req-un [:ivr.config.layer.load/http-retry-timeout-s
                                                        :ivr.config.layer.load/http-retry-delay-s]))
           :ret any?)
(defmulti load-layer
  (fn layer-type [layer _]
    (cond
      (spec/valid? :ivr.config/http-layer layer) :http-layer
      (spec/valid? :ivr.config/file-layer layer) :file-layer
      (spec/valid? :ivr.config/object-layer layer) :object-layer
      :else :invalid-layer)))
