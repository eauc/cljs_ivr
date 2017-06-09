(ns ivr.models.node.route
  (:require [ivr.models.node :as node]
            [ivr.models.node-set :as node-set]))

(defn- conform-match [match]
  (if (string? match)
    {"next" match}
    (node-set/conform-set match "set")))


(defn- conform-case [node]
  (if (map? (get node "case"))
    (update node "case" #(into {} (for [[k v] %] [k (conform-match v)])))
    (dissoc node "case")))


(defmethod node/conform-type "route"
  [node]
  (conform-case node))


(defmethod node/enter-type "route"
  [{:strs [varname case] :as node}
   {:keys [call deps] :as context}]
  (let [{:strs [next set]} (->> (get-in call [:action-data varname])
                                (get case))
        update-action-data (node-set/apply-set set call)
        result (node/go-to-next (assoc node "next" next) deps)]
    (merge update-action-data result)))


(defmethod node/leave-type "route"
  [node {:keys [deps]}]
  (node/go-to-next node deps))
