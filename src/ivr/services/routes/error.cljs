(ns ivr.services.routes.error)


(defn error-response
  [{:keys [status]
    :or {status 500}
    :as data}]
  {:status status
   :data data})
