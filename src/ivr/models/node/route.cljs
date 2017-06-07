(ns ivr.models.node.route
  (:require [ivr.models.node :as node]))

(defn- conform-match [match]
  (if (string? match)
    {"next" match}
    (node/conform-set match "set")))


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
  (let [action-data (:action-data call)
        {:strs [next set]} (->> (get action-data varname)
                                (get case))
        new-data (node/apply-data-set action-data set)
        update-action-data (if-not (= action-data new-data)
                             {:ivr.call/action-data (assoc call :action-data new-data)}
                             {})
        result (node/go-to-next (assoc node "next" next) deps)]
    (merge update-action-data result)))


(defmethod node/leave-type "route"
  [node {:keys [deps]}]
  (node/go-to-next node deps))
