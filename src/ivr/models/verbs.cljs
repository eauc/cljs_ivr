(ns ivr.models.verbs
  (:require [cljs.spec :as spec]
            [cljs.nodejs :as nodejs]
            [hiccups.runtime :as hiccupsrt]
            [ivr.models.verb-base :as verb-base]
            [ivr.models.verbs.gather]
            [ivr.models.verbs.hangup]
            [ivr.models.verbs.play]
            [ivr.models.verbs.redirect])
  (:require-macros [hiccups.core :as hiccup :refer [html]]))

(spec/def ::verbs
  (spec/coll-of :ivr.models.verb-base/verb :kind vector?))

(defn clj->xml [data]
  (html data))

(spec/fdef create
           :args (spec/cat :verbs ::verbs))
(defn create [verbs]
  (let [verbs-xml [:Response (for [verb verbs] (verb-base/create-type verb))]]
    {:content-type "application/xml"
     :data (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                (clj->xml verbs-xml))}))
