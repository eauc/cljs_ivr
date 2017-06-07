(ns ivr.models.node.dtmf-catch-speak
  (:require [clojure.string :as str]))

(defn- look-like-fr-phone-number [value]
  (re-find #"(\+33|0)([1-9]\d{8})$" value))


(defn- split-fr-phone-number [value]
  (let [value-fr-local (str/replace value #"(\+33)([1-9]\d{8})$" "0$2")
        split-value (str/split value-fr-local #"(\d{2})")]
    split-value))


(defn- speak-phone-number [value voice]
  (let [split-value (if (look-like-fr-phone-number value)
                      (split-fr-phone-number value)
                      (vec value))]
    (->> split-value
         (remove empty?)
         (mapv (fn [value] {:text (str value ".") :voice voice})))))


(defn- speak-action-var [action-data {:strs [varname voice pronounce] :as sound}]
  (let [value (get action-data varname)]
    (cond
      (nil? value) []
      (not (= "phone" pronounce)) [{:text value :voice voice}]
      :else (speak-phone-number value voice))))
