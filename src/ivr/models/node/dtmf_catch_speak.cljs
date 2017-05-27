(ns ivr.models.node.dtmf-catch-speak
  (:require [clojure.string :as str]
            [cljs.spec :as spec]))


(defn- look-like-fr-phone-number [value]
  (re-find #"(\+33|0)([1-9]\d{8})$" value))


(spec/fdef speak-phone-number
           :args (spec/cat :value string?)
           :ret (spec/coll-of string?))
(defn- split-fr-phone-number [value]
  (let [value-fr-local (str/replace value #"(\+33)([1-9]\d{8})$" "0$2")
        split-value (str/split value-fr-local #"(\d{2})")]
    split-value))


(spec/fdef speak-phone-number
           :args (spec/cat :value string?
                           :voice :ivr.node.dtmf-catch.sound/voice)
           :ret (spec/coll-of :ivr.node.dtmf-catch.sound/play))
(defn- speak-phone-number [value voice]
  (let [split-value (if (look-like-fr-phone-number value)
                      (split-fr-phone-number value)
                      (vec value))]
    (->> split-value
         (remove empty?)
         (mapv (fn [value]
                 {:text (str value ".")
                  :voice voice})))))


(spec/fdef speak-action-var
           :args (spec/cat :action-data :ivr.call/action-data
                           :sound :ivr.node.dtmf-catch.sound/speak)
           :ret (spec/coll-of :ivr.node.dtmf-catch.sound/play))
(defn- speak-action-var [action-data {:keys [varname voice pronounce]}]
  (let [value (get action-data (keyword varname))]
    (cond
      (nil? value) []
      (not (= "phone" pronounce)) [{:text value :voice voice}]
      :else (speak-phone-number value voice))))
