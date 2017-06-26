(ns ivr.models.call-number
  (:require [cljs.nodejs :as nodejs]
            [clojure.set :as set]))


(defonce path-lib (nodejs/require "path"))
(defonce arcep (js->clj (nodejs/require (.join path-lib (.cwd js/process) "./config/arcep.json"))))


(defn find-arcep-slice
  [number]
  (first (filter (fn [slice] (and (>= number (get slice "begin"))
                                  (<= number (get slice "end")))) arcep)))


(defn find-arcep-info
  [number]
  (or (some-> (or number "")
              (->> (re-find #"^(\+33)?(\d+)$"))
              (nth 2)
              (js/Number)
              (find-arcep-slice)
              (select-keys ["dep" "reg" "zte"])
              (set/rename-keys {"zte" "zoneTel"}))
      {}))
