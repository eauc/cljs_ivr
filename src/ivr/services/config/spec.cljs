(ns ivr.services.config.spec
  (:require [cljs.spec :as spec]
            [ivr.libs.logger :as logger]))

(spec/def :ivr.config/config
  map?)

(spec/def :ivr.config.layer/desc
  string?)

(spec/def :ivr.config.layer.file/path
  string?)

(defn- url? [string]
  (re-find #"^http://" string))

(spec/def :ivr.config.layer.url/path
  (spec/and string? url?))

(spec/def :ivr.config/file-layer
  (spec/keys :req-un [:ivr.config.layer.file/path]))

(spec/def :ivr.config/http-layer
  (spec/keys :req-un [:ivr.config.layer.url/path]))

(spec/def :ivr.config/object-layer
  (spec/keys :req-un [:ivr.config.layer/desc :ivr.config/config]))

(spec/def :ivr.config/layer
  (spec/or :file :ivr.config/file-layer
           :object :ivr.config/object-layer
           :url :ivr.config/url-layer))

(spec/def :ivr.config/layers
  (spec/coll-of :ivr.config/layer))

(spec/def :ivr.config.layer.load/http-retry-timeout-s
  integer?)

(spec/def :ivr.config.layer.load/http-retry-delay-s
  integer?)

(spec/def :ivr.config.layer.load/error
  string?)

(spec/def :ivr.config.layer.load/result
  (spec/keys :req-un [:ivr.config.layer/desc :ivr.config/config]
             :opt-un [:ivr.config.layer.load/error]))

(spec/def :ivr.config/loads
  (spec/coll-of :ivr.config.layer.load/result :kind vector?))

(spec/def :ivr.config/info
  (spec/keys :req-un [:ivr.config/config
                      :ivr.config/loads]))
