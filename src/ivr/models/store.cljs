(ns ivr.models.store
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.libs.logger :as logger]
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


(defn- get-account-success
  [[event-name event-payload] response]
  (let [account (or (aget response "body") {})]
    [event-name (assoc event-payload :account account)]))


(defmethod query :ivr.store/get-account
  [{:keys [id on-success on-error]}]
  {:method "GET"
   :url (str "/cloudstore/account/" id)
   :on-success (partial get-account-success on-success)
   :on-error on-error})


(defn- get-script-success
  [[event-name event-payload] response]
  (let [script (or (aget response "body") {})]
    [event-name (assoc event-payload :script script)]))


(defmethod query :ivr.store/get-script
  [{:keys [account-id script-id on-success on-error]}]
  {:method "GET"
   :url (str "/cloudstore/account/" account-id "/script/" script-id)
   :on-success (partial get-script-success on-success)
   :on-error on-error})


(defn- get-sound-success
  [query response]
  (let [{:keys [account-id script-id name on-success]} query
        body (or (aget response "body") {})]
    (if (= 0 (or (get-in body ["meta" "total_count"]) 0))
      [:ivr.routes/error
       {:status 500
        :statusCode "sound_not_found"
        :message "Sound not found"
        :cause {:account-id account-id
                :script-id script-id
                :name name}}]
      (let [id (get-in body ["objects" 0 "_id"])
            url (str "/cloudstore/file/" id)
            [on-success-event on-success-payload] on-success]
        [on-success-event (assoc on-success-payload :sound-url url)]))))


(defn- get-file-error
  [query error]
  [:ivr.routes/error
   {:status 404
    :status_code "file_not_found"
    :message "File not found"
    :cause (-> query
               (dissoc :on-success :on-error)
               (assoc :message (:message error)))}])


(defmethod query :ivr.store/get-sound-by-name
  [{:keys [name account-id script-id] :as query}]
  (let [url (str "/cloudstore/account/" account-id "/file")]
    {:method "GET"
     :url url
     :query {:query {:filename name
                     :metadata.type "scriptSound"
                     :metadata.script script-id}}
     :on-success (partial get-sound-success query)
     :on-error (partial get-file-error (assoc query :url url))}))
