(ns ivr.libs.json
  (:require [cognitect.transit :as transit]
            [clojure.walk :as walk]
            [ivr.libs.logger :as logger]))


(def json-reader
  (transit/reader :json))


(defn- json->clj
  [json-string & args]
  (->> (or json-string "{}")
       (logger/default "silly" "json->clj")
       (transit/read json-reader)
       walk/keywordize-keys))


(def json-writer
  (transit/writer :json-verbose))


(defn- clj->json
  [data]
  (->> data
       walk/stringify-keys
       (transit/write json-writer)
       (logger/default "silly" "clj->json")))
