(ns ivr.models.node.dtmf-catch
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.models.node.dtmf-catch-speak :as dc-speak]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [ivr.specs.node.dtmf-catch]
            [re-frame.core :as re-frame]))

(defn- conform-sound [sound]
  (if (and (string? (:varname sound))
           (string? (:voice sound)))
    (-> sound
        (assoc :type :ivr.node.dtmf-catch/speak)
        (update :pronounce #(if (= "phone" %) % "normal")))
    (assoc sound :type :ivr.node.dtmf-catch/sound)))


(defn- conform-welcome [node]
  (-> node
      (update :retry #(if-not (integer? %) 0 %))
      (update :welcome #(or % []))
      (update :welcome #(if-not (vector? %) (vector %) %))
      (update :welcome #(mapv conform-sound %))))


(defmethod node/conform-type "dtmfcatch"
  [node]
  (-> node
      node/conform-preset
      conform-welcome))


(spec/fdef play-response
           :args (spec/cat :plays (spec/coll-of :ivr.node.dtmf-catch.sound/play)
                           :node :ivr.node.dtmf-catch/node
                           :options :ivr.node.dtmf-catch/options)
           :ret map?)
(defn- play-response
  [plays
   {:keys [id account-id script-id] :as node}
   {:keys [retries verbs] :as options}]
  (let [callback-url (str (url/absolute [:v1 :action :script-leave-node]
                                        {:script-id script-id
                                         :node-id id})
                          "?retries=" retries)]
    {:ivr.routes/response
     (verbs
      [(merge {:numdigits -1}
              (select-keys node [:finishonkey :timeout :numdigits])
              {:type :ivr.verbs/gather
               :callbackurl callback-url
               :play plays})
       {:type :ivr.verbs/redirect
        :path callback-url}])}))


(spec/fdef play-sound-name-request
           :args (spec/cat :sound :ivr.node.dtmf-catch/sound
                           :loaded (spec/coll-of :ivr.node.dtmf-catch.sound/play)
                           :rest (spec/nilable (spec/coll-of :ivr.node.dtmf-catch/sound))
                           :node :ivr.node.dtmf-catch/node
                           :options :ivr.node.dtmf-catch/options)
           :ret map?)
(defn- play-sound-name-request
  [sound loaded rest
   {:keys [account-id script-id] :as node}
   {:keys [store] :as options}]
  {:ivr.web/request
   (store
    {:type :ivr.store/get-sound-by-name
     :name (:soundname sound)
     :account-id account-id
     :script-id script-id
     :on-success [::play-sound
                  {:options options
                   :node node
                   :loaded loaded
                   :rest rest}]})})


(spec/fdef play-resolve-sounds
           :args (spec/cat :loaded (spec/coll-of :ivr.node.dtmf-catch.sound/play)
                           :sounds (spec/coll-of :ivr.node.dtmf-catch/sound)
                           :node :ivr.node.dtmf-catch/node
                           :options :ivr.node.dtmf-catch/options)
           :ret map?)
(defn- play-resolve-sounds
  [already-loaded sounds
   {:keys [id account-id script-id] :as node}
   {:keys [action-data retries store verbs] :as options}]
  (loop [loaded already-loaded
         [sound & rest] sounds]
    (cond
      (nil? sound) (play-response loaded node options)
      (= :ivr.node.dtmf-catch/speak (:type sound))
      (let [speak-sound (dc-speak/speak-action-var action-data sound)]
        (recur (vec (concat loaded speak-sound)) rest))
      :else (play-sound-name-request sound loaded rest node options))))



(spec/fdef play-retry
           :args (spec/cat :node :ivr.node.dtmf-catch/node
                           :retries :ivr.node.dtmf-catch/retries
                           :options :ivr.node.dtmf-catch/options)
           :ret map?)
(defn- play-retry [{:keys [retry welcome] :as node}
                   retries options]
  (let [sounds (if (> retries 1) (drop retry welcome) welcome)]
    (play-resolve-sounds [] sounds node (assoc options :retries retries))))


(defmethod node/enter-type "dtmfcatch"
  [node {:keys [action-data] :as options}]
  (let [update-action-data (node/apply-preset node options)
        new-action-data (or (get-in update-action-data [:ivr.call/action-data :data])
                            action-data)
        result (play-retry node 1 (assoc options :action-data new-action-data))]
    (merge update-action-data result)))


(defn play-sound-event [_ [_ {:keys [loaded node options rest sound-url]}]]
  (play-resolve-sounds (conj loaded sound-url) rest node options))
(re-frame/reg-event-fx
 ::play-sound
 [routes/interceptor
  db/default-interceptors]
 play-sound-event)
