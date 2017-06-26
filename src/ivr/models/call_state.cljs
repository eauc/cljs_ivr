(ns ivr.models.call-state
  (:require [ivr.libs.logger :as logger]))

(def log
  (logger/create "callState"))


(defn current-state
  [call]
  (get-in call [:state :current]))


(defmulti enter-ticket (fn [_ _ next-state] next-state))

(defmethod enter-ticket :default
  []
  {})


(defmulti leave-ticket #(current-state %1))

(defmethod leave-ticket :default
  []
  {})


(defmulti end-cause (fn [_ from] from))

(defmethod end-cause :default
  []
  nil)


(defmulti on-enter (fn [_ {:keys [to]}] to))

(defmethod on-enter :default
  []
  {})


(defmulti on-leave (fn [_ {:keys [from]}] from))

(defmethod on-leave :default
  []
  {})
