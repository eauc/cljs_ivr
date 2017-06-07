(ns ivr.models.node.smtp
  (:require [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.services.routes :as routes]))

(def log
  (logger/create "node.smtp"))


(defmethod node/conform-type "smtp"
  [node]
  node)


(defmethod node/enter-type "smtp"
  [{:strs [account_id] :as node}
   {{:keys [action-data]} :call deps :deps}]
  (let [{:keys [services]} deps
        mail-options (select-keys node ["subject" "to" "attachment" "text"])
        mail-request
        {:ivr.web/request
         (services
           {:type :ivr.services/send-mail
            :account-id account_id
            :context action-data
            :options mail-options
            :on-success [::send-mail-success {:node node}]
            :on-error [::send-mail-error {:node node}]})}]
    (merge mail-request
           (node/go-to-next node deps))))


(defn- send-mail-success
  [_ info]
  (log "info" "send mail success" (dissoc info :response))
  {})

(routes/reg-action
  ::send-mail-success
  send-mail-success)


(defn- send-mail-error
  [_ info]
  (log "error" "send mail error" info)
  {})

(routes/reg-action
  ::send-mail-error
  send-mail-error)


(defmethod node/leave-type "smtp"
  [node {:keys [deps]}]
  (node/go-to-next node deps))
