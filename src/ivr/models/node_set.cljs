(ns ivr.models.node-set
  (:require [ivr.libs.logger :as logger]
            [ivr.models.call :as call]))


(def log
  (logger/create "nodeSet"))


(defprotocol SetEntryProtocol
  (apply-set-entry [entry call-data]))


(defrecord SetEntry
    [value to]
  SetEntryProtocol
  (apply-set-entry [entry call-data]
    (assoc call-data (:to entry) (:value entry))))


(defrecord CopyEntry
    [from to]
  SetEntryProtocol
  (apply-set-entry [entry call-data]
    (let [value (get call-data (:from entry))]
      (assoc call-data (:to entry) value))))


(defn- ->set-entry [set]
  (let [varname (get set "varname")
        value (get set "value")]
    (if (and (string? value)
             (string? varname) (not (empty? varname)))
      (if (re-find #"^\$" value)
        (->CopyEntry (subs value 1) varname)
        (->SetEntry value varname)))))


(defn conform-set [map key]
  (let [set (get map key)]
    (if-not (nil? set)
      (assoc map key (cond-> set
                       (map? set) (vector)
                       (not (nil? set)) (->> (mapv ->set-entry)
                                             (remove nil?)
                                             (vec))))
      (dissoc map key))))


(defn conform-preset [node]
  (conform-set node "preset"))


(defn apply-set
  [set {:keys [action-data] :as call}]
  (let [new-data (reduce #(apply-set-entry %2 %1) action-data set)]
    (if-not (= new-data action-data)
      {:ivr.call/update
       {:id (call/id call) :action-data new-data}}
      {})))


(defn apply-preset
  [{:strs [preset] :as node} call]
  (apply-set preset call))
