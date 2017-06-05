(ns ivr.models.node.smtp
  (:require [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.services.routes :as routes]
            [re-frame.core :as re-frame]))


(def log
  (logger/create "node.smtp"))


(defmethod node/conform-type "smtp"
  [node]
  node)


(defmethod node/enter-type "smtp"
  [{:keys [account-id] :as node}
   {{:keys [action-data]} :call deps :deps}]
  (let [mail-options (select-keys node [:subject :to :attachment :text])
        mail-request
        {:ivr.web/request
         {:method "POST"
          :url (str "/smartccivrservices/account/" account-id "/mail")
          :data {:context action-data
                 :mailOptions mail-options}
          :on-success [::send-mail-success {:node node}]
          :on-error [::send-mail-error {:node node}]}}]
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
