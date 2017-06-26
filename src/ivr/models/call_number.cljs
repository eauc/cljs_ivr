(ns ivr.models.call-number
  (:require [cljs.nodejs :as nodejs]
            [clojure.set :as set]
            [clojure.string :as str]
            [ivr.libs.logger :as logger]))


(def log
  (logger/create "callNumber"))


(defonce path-lib (nodejs/require "path"))
(defonce arcep (js->clj (nodejs/require (.join path-lib (.cwd js/process) "./config/arcep.json"))))
(defonce dep->reg-zone-tel (js->clj (nodejs/require (.join path-lib (.cwd js/process) "./config/depRegZoneTel.json"))))


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


(defn sip-header->paccess-info
  [header]
  (->> (str/split header #"; ")
       (map (fn [tab-value]
              (if (re-find #"-" tab-value)
                (str/split tab-value #"-")
                [])))
       (remove empty?)
       (into {})))


(defn find-paccess-info
  [header]
  (let [departement (some-> (sip-header->paccess-info header)
                            (get "CP")
                            (subs 0 2))
        reg-zone-tel (-> (get dep->reg-zone-tel departement)
                         (set/rename-keys {"zte" "zoneTel"}))]
    (if (and departement reg-zone-tel)
      (merge reg-zone-tel {"dep" departement})
      {})))


(defn geo-localize-call
  [{:keys [from paccess-header]
    :or {paccess-header ""}
    :as params}]
  (let [arcep-info (find-arcep-info from)
        paccess-info (if (= 30 (get arcep-info "zoneTel"))
                       (find-paccess-info paccess-header))]
    (merge
      arcep-info
      paccess-info)))
