(ns ivr.models.node.dtmf-catch
  (:require [cljs.spec :as spec]
            [ivr.db :as db]
            [ivr.models.node :as node]
            [ivr.models.node.dtmf-catch-speak :as dc-speak]
            [ivr.routes.url :as url]
            [ivr.services.routes :as routes]
            [ivr.specs.node.dtmf-catch]
            [re-frame.core :as re-frame]
            [ivr.libs.logger :as logger]))


(def log
  (logger/create "node.dtmf-catch"))

(defn- conform-sound
  [sound]
  (if (and (string? (:varname sound))
           (string? (:voice sound)))
    (-> sound
        (assoc :type :ivr.node.dtmf-catch/speak)
        (update :pronounce #(if (= "phone" %) % "normal")))
    (assoc sound :type :ivr.node.dtmf-catch/sound)))


(defn- conform-welcome
  [node]
  (-> node
      (update :retry #(if-not (integer? %) 0 %))
      (update :welcome #(or % []))
      (update :welcome #(if-not (vector? %) (vector %) %))
      (update :welcome #(mapv conform-sound %))))


(defn- conform-dtmf-ok
  [case]
  (if (contains? case :dtmf_ok)
    (-> case
        (update :dtmf_ok node/conform-set :set)
        (update-in [:dtmf_ok :next] keyword))
    case))


(defn- conform-max-attempt-reached
  [case]
  (if (contains? case :max_attempt_reached)
    (update case :max_attempt_reached keyword)
    case))


(defn- conform-case
  [node]
  (if (contains? node :case)
    (-> node
        (update :case conform-dtmf-ok)
        (update :case conform-max-attempt-reached))
    node))


(defmethod node/conform-type "dtmfcatch"
  [node]
  (-> node
      node/conform-preset
      (update :max_attempts #(if-not (nil? %) (int %) 0))
      conform-welcome
      conform-case))


;; (spec/fdef gather
;;            :args (spec/cat :plays (spec/coll-of :ivr.node.dtmf-catch.sound/play)
;;                            :node :ivr.node.dtmf-catch/node
;;                            :options :ivr.node.dtmf-catch/options)
;;            :ret map?)
(defn- gather
  [plays
   {:keys [id account-id script-id] :as node}
   {:keys [retries] {:keys [verbs]} :deps}]
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


;; (spec/fdef sound-name-request
;;            :args (spec/cat :sound :ivr.node.dtmf-catch/sound
;;                            :loaded (spec/coll-of :ivr.node.dtmf-catch.sound/play)
;;                            :rest (spec/nilable (spec/coll-of :ivr.node.dtmf-catch/sound))
;;                            :node :ivr.node.dtmf-catch/node
;;                            :options :ivr.node.dtmf-catch/options)
;;            :ret map?)
(defn- sound-name-request
  [sound loaded rest
   {:keys [account-id script-id] :as node}
   {:keys [call retries] {:keys [store]} :deps}]
  {:ivr.web/request
   (store
     {:type :ivr.store/get-sound-by-name
      :name (:soundname sound)
      :account-id account-id
      :script-id script-id
      :on-success [::sound-name-success
                   {:call call
                    :node node
                    :retries retries
                    :loaded loaded
                    :rest rest}]})})


;; (spec/fdef resolve-sounds
;;            :args (spec/cat :loaded (spec/coll-of :ivr.node.dtmf-catch.sound/play)
;;                            :sounds (spec/coll-of :ivr.node.dtmf-catch/sound)
;;                            :node :ivr.node.dtmf-catch/node
;;                            :options :ivr.node.dtmf-catch/options)
;;            :ret map?)
(defn- resolve-sounds
  [already-loaded sounds
   {:keys [id account-id script-id] :as node}
   {:keys [call] :as context}]
  (loop [loaded already-loaded
         [sound & rest] sounds]
    (cond
      (nil? sound) (gather loaded node context)
      (= :ivr.node.dtmf-catch/speak (:type sound))
      (let [speak-sound (-> (:action-data call)
                            (dc-speak/speak-action-var sound))]
        (recur (vec (concat loaded speak-sound)) rest))
      :else (sound-name-request sound loaded rest node context))))


;; (spec/fdef retry
;;            :args (spec/cat :node :ivr.node.dtmf-catch/node
;;                            :retries :ivr.node.dtmf-catch/retries
;;                            :options :ivr.node.dtmf-catch/options)
;;            :ret map?)
(defn- retry
  [{:keys [retry welcome] :as node}
   {:keys [retries] :as context}]
  (let [sounds (if (> retries 1) (drop retry welcome) welcome)]
    (resolve-sounds [] sounds node context)))


(defmethod node/enter-type "dtmfcatch"
  [node {:keys [call deps] :as context}]
  (let [update-action-data (node/apply-preset node call)
        updated-call (or (get update-action-data :ivr.call/action-data) call)
        result (retry node {:deps deps :call updated-call :retries 1})]
    (merge update-action-data result)))


(defn sound-name-success
  [deps {:keys [call node retries loaded rest sound-url]}]
  (resolve-sounds (conj loaded sound-url) rest node
                  {:deps deps :call call :retries retries}))

(routes/reg-action
  ::sound-name-success
  sound-name-success)


(spec/fdef validate-digits-pattern
           :args (spec/cat :pattern :ivr.node.dtmf-catch/validationpattern
                           :digits :ivr.node.dtmf-catch/digits)
           :ret boolean?)
(defn- validate-digits-pattern [pattern digits]
  (let [regex (re-pattern pattern)]
    (every? #(re-find regex %) digits)))


(spec/fdef digits-valid?
           :args (spec/cat :node :ivr.node.dtmf-catch/node
                           :params :ivr.node/params)
           :ret boolean?)
(defn- digits-valid?
  [{:keys [finishonkey numdigits validationpattern] :as node}
   {:keys [digits termdigit] :as params}]
  (let [num-digits-ok? (or (nil? numdigits) (= numdigits (count digits)))
        term-digit-ok? (or (nil? finishonkey) (= finishonkey termdigit))
        pattern-ok? (or (nil? validationpattern) (nil? digits)
                        (validate-digits-pattern validationpattern digits))]
    (log "debug" "digits-valid?" {:num-digits-ok? num-digits-ok?
                                  :term-digit-ok? term-digit-ok?
                                  :pattern-ok? pattern-ok?})
    (and num-digits-ok? term-digit-ok? pattern-ok?)))


;; (spec/fdef save-action-data
;;            :args (spec/cat :node :ivr.node.dtmf-catch/node
;;                            :options :ivr.node/options)
;;            :ret map?)
(defn- save-action-data
  [{:keys [varname] :as node}
   params
   {:keys [action-data] :as call}]
  (let [{:keys [digits]} params
        {:keys [set]} (get-in node [:case :dtmf_ok])
        new-data (-> action-data
                     (assoc (keyword varname) digits)
                     (node/apply-data-set set))]
    {:ivr.call/action-data (assoc call :action-data new-data)}))


;; (spec/fdef leave-success
;;            :args (spec/cat :node :ivr.node.dtmf-catch/node
;;                            :options :ivr.node/options)
;;            :ret map?)
(defn- leave-success
  [node {:keys [call deps params] :as context}]
  (let [update-action-data (save-action-data node params call)
        {:keys [next]} (get-in node [:case :dtmf_ok])
        result (node/go-to-next (assoc node :next next) deps)]
    (merge update-action-data result)))


;; (spec/fdef leave-max-attempts-reached
;;            :args (spec/cat :node :ivr.node.dtmf-catch/node
;;                            :options :ivr.node/options)
;;            :ret map?)
(defn- leave-max-attempts-reached
  [node {:keys [deps]}]
  (log "debug" "leave-max-attempts-reached")
  (let [next (get-in node [:case :max_attempt_reached])]
    (node/go-to-next (assoc node :next next) deps)))


;; (spec/fdef leave-retry
;;            :args (spec/cat :node :ivr.node.dtmf-catch/node
;;                            :options :ivr.node/options)
;;            :ret map?)
(defn- leave-retry
  [{:keys [max_attempts] :as node}
   {:keys [call deps params] :as context}]
  (let [retries (int (:retries params))]
    (if (<= max_attempts retries)
      (leave-max-attempts-reached node context)
      (retry node {:call call :deps deps :retries (inc retries)}))))


(defmethod node/leave-type "dtmfcatch"
  [node {:keys [params] :as context}]
  (if (digits-valid? node params)
    (leave-success node context)
    (leave-retry node context)))
