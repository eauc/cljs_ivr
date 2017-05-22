(ns ivr.models.store
  (:require [re-frame.core :as re-frame]
            ;; [ivr.services.routes :as routes]
            [cljs.spec :as spec]
            ;; [ivr.db :as db]
            ))

(spec/def ::type
  keyword?)

(spec/def ::query
  (spec/keys :req-un [::type]))

(spec/fdef query
           :args (spec/cat :query ::query)
           :ret map?)
(defmulti query :type)

(defmethod query :ivr.store/get-script
  [{:keys [account-id script-id on-success on-error]}]
  {:method "GET"
   :url (str "/cloudstore/account/" account-id "/script/" script-id)
   :on-success on-success
   :on-error on-error})

;; (defmethod query :ivr.store/get-sound-by-name
;;   [{:keys [name account-id script-id] :as query}]
;;   (let [url (str "/cloudstore/account/" account-id "/file")]
;;     {:method "GET"
;;      :url url
;;      :query {:query {:filename name
;;                      :metadata.type "scriptSound"
;;                      :metadata.script script-id}}
;;      :on-success [::get-sound-success query]
;;      :on-error [::get-file-error (assoc query :url url)]}))

;; (defn get-sound-success [{:keys [account-id script-id name on-success] :as query} response]
;;    (if (= 0 (or (aget response "body" "meta" "total_count") 0))
;;      {:ivr.routes/response
;;       (routes/error-response
;;        {:status 500
;;         :statusCode "sound_not_found"
;;         :message "Sound not found"
;;         :cause {:account-id account-id
;;                 :script-id script-id
;;                 :name name}})}
;;      (let [id (aget response "body" "objects" 0 "_id")
;;            url (str "/cloudstore/file/" id)]
;;        {:dispatch (vec (conj on-success url))})))

;; (re-frame/reg-event-fx
;;  ::get-sound-success
;;  [routes/interceptor
;;   db/default-interceptors]
;;  (fn get-sound-success-fx [_ [_ query response]]
;;    (get-sound-success query response)))

;; (defn get-file-error [query error]
;;   {:ivr.routes/response
;;    (routes/error-response
;;     {:status 404
;;      :status_code "file_not_found"
;;      :message "File not found"
;;      :cause (-> query
;;                 (dissoc :on-success :on-error)
;;                 (assoc :message (:message error)))})})

;; (re-frame/reg-event-fx
;;  ::get-file-error
;;  [routes/interceptor
;;   db/default-interceptors]
;;  (fn get-file-error-fx [_ [_ query error]]
;;    (get-file-error query error)))