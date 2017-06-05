(ns ivr.models.store
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
            [ivr.services.routes :as routes]
            [ivr.specs.store]
            [re-frame.core :as re-frame]))

(spec/fdef query
           :args (spec/cat :query :ivr.store/query)
           :ret map?)
(defmulti query :type)

(re-frame/reg-cofx
  :ivr.store/cofx
  (fn store-cofx [coeffects _]
    (assoc coeffects :store query)))


(defmethod query :ivr.store/get-account
  [{:keys [id on-success on-error]}]
  {:method "GET"
   :url (str "/cloudstore/account/" id)
   :on-success on-success
   :on-error on-error})


(defmethod query :ivr.store/get-script
  [{:keys [account-id script-id on-success on-error]}]
  {:method "GET"
   :url (str "/cloudstore/account/" account-id "/script/" script-id)
   :on-success on-success
   :on-error on-error})


(defmethod query :ivr.store/get-sound-by-name
  [{:keys [name account-id script-id] :as query}]
  (let [url (str "/cloudstore/account/" account-id "/file")]
    {:method "GET"
     :url url
     :query {:query {:filename name
                     :metadata.type "scriptSound"
                     :metadata.script script-id}}
     :on-success [::get-sound-success {:query query}]
     :on-error [::get-file-error {:query (assoc query :url url)}]}))


(defn get-sound-success
  [_ {:keys [query response]}]
  (let [{:keys [account-id script-id name on-success]} query
        body (aget response "body")]
    (if (= 0 (or (get-in body [:meta :total_count]) 0))
      {:ivr.routes/response
       (routes/error-response
         {:status 500
          :statusCode "sound_not_found"
          :message "Sound not found"
          :cause {:account-id account-id
                  :script-id script-id
                  :name name}})}
      (let [id (get-in body [:objects 0 :_id])
            url (str "/cloudstore/file/" id)
            [on-success-event on-success-payload] on-success]
        {:ivr.routes/dispatch
         [on-success-event (assoc on-success-payload :sound-url url)]}))))

(routes/reg-action
  ::get-sound-success
  get-sound-success)


(defn get-file-error
  [_ {:keys [query error]}]
  {:ivr.routes/response
   (routes/error-response
     {:status 404
      :status_code "file_not_found"
      :message "File not found"
      :cause (-> query
                 (dissoc :on-success :on-error)
                 (assoc :message (:message error)))})})

(routes/reg-action
  ::get-file-error
  get-file-error)
