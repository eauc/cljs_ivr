(ns ivr.models.node.route
  (:require [ivr.models.node :as node]))


(defn- conform-match [match]
  (if (string? match)
    {:next (keyword match)}
    (-> match
        (update :next keyword)
        (node/conform-set :set))))


(defn- conform-case [node]
  (if (map? (:case node))
    (update node :case #(into {} (for [[k v] %] [k (conform-match v)])))
    (dissoc node :case)))


(defmethod node/conform-type "route"
  [node]
  (-> node
      (update :varname keyword)
      conform-case))


(defmethod node/enter-type "route"
  [{:keys [varname case] :as node}
   {:keys [action-data call-id] :as options}]
  (let [value (keyword (get action-data varname))
        match (get case value)
        {:keys [next set]} match
        new-data (node/apply-data-set action-data set)
        update-action-data (if-not (= action-data new-data)
                             {:ivr.call/action-data {:call-id call-id
                                                     :data new-data}}
                             {})
        result (node/go-to-next (assoc node :next next) options)]
    (merge update-action-data result)))


(defmethod node/leave-type "route"
  [node options]
  (node/go-to-next node options))
