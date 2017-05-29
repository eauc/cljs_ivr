(ns ivr.routes.url
  (:require [clojure.string :as s]))

(def config
  {:module "IVR"
   :version "1.0.0"
   :apis {:v1 {:link  "/smartccivr"
               :config {:link "/config"
                        :explain "/"}
               :action {:link "/script/:script_id"
                        :script-start "/node/start"
                        :script-enter-node "/node/:node_id"
                        :script-leave-node "/node/:node_id/callback"}
               :status {:link "/script/:script_id"
                        :dial "/dialstatus"}}}})

(defn- relative [api-path]
  (let [value (get-in (:apis config) api-path)]
    (if (string? value)
      value
      (:link value))))

(defn- replace-param [url [name value]]
  (let [pattern (s/replace (str name) #"-" "_")]
    (s/replace url pattern value)))

(defn- replace-params [url params]
  (reduce #(replace-param %1 %2) url params))

(defn absolute
  ([api-path]
   (let [last-keyword (last api-path)
         path (if (= :link last-keyword)
                (take (dec (count api-path)) api-path)
                api-path)]
     (->> path
          count
          range
          (map inc)
          (map #(take % path))
          (map relative)
          (apply str))))
  ([api-path params]
   (-> api-path
       absolute
       (replace-params params))))

(defn describe
  ([api-path]
   (let [value (get-in (:apis config) api-path)]
     (if (string? value)
       (absolute api-path)
       (into {} (for [[k v] value]
                  [k (describe (conj api-path k))]))))))
