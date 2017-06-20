(ns ivr.models.node.dtmf-catch
  (:require [clojure.walk :as walk]
            [ivr.libs.logger :as logger]
            [ivr.models.node :as node]
            [ivr.models.node-set :as node-set]
            [ivr.models.node.dtmf-catch-speak :as dc-speak]
            [ivr.routes.url :as url]))

(def log
  (logger/create "node.dtmf-catch"))

(defn- conform-sound
  [sound]
  (if (and (string? (get sound "varname"))
           (string? (get sound "voice")))
    (let [pronounce (get sound "pronounce")]
      (-> sound
          (assoc :type :ivr.node.dtmf-catch/speak)
          (cond->
              (not (= "phone" pronounce)) (assoc "pronounce" "normal"))))
    (assoc sound :type :ivr.node.dtmf-catch/sound)))


(defn- conform-welcome
  [node]
  (-> node
      (update "retry" #(if-not (integer? %) 0 %))
      (update "welcome" #(or % []))
      (update "welcome" #(if-not (vector? %) (vector %) %))
      (update "welcome" #(mapv conform-sound %))))


(defn- conform-dtmf-ok
  [dtmf-ok]
  (cond-> dtmf-ok
    (not (map? dtmf-ok)) (->> (assoc {} "next"))
    :always (node-set/conform-set "set")))


(defn- conform-case
  [case]
  (cond-> case
    (contains? case "dtmf_ok") (update "dtmf_ok" conform-dtmf-ok)))


(defmethod node/conform-type "dtmfcatch"
  [node]
  (-> node
      node-set/conform-preset
      (update "max_attempts" #(if-not (nil? %) (int %) 0))
      conform-welcome
      (cond->
          (contains? node "case") (update "case" conform-case))))


(defn- gather
  [plays
   {:strs [id script_id] :as node}
   {:keys [retries] {:keys [verbs]} :deps}]
  (let [callback-url (str (url/absolute [:v1 :action :script-leave-node]
                                        {:script-id script_id
                                         :node-id id})
                          "?retries=" retries)]
    {:ivr.routes/response
     (verbs
       [(merge {:numdigits -1}
               (-> node
                   (select-keys ["finishonkey" "timeout" "numdigits"])
                   walk/keywordize-keys)
               {:type :ivr.verbs/gather
                :callbackurl callback-url
                :play plays})
        {:type :ivr.verbs/redirect
         :path callback-url}])}))


(defn- sound-name-request
  [sound loaded rest
   {:strs [account_id script_id] :as node}
   {:keys [call retries] {:keys [store]} :deps}]
  {:ivr.web/request
   (store
     {:type :ivr.store/get-sound-by-name
      :name (get sound "soundname")
      :account-id account_id
      :script-id script_id
      :on-success [::sound-name-success
                   {:call call
                    :node node
                    :retries retries
                    :loaded loaded
                    :rest rest}]})})


(defn- resolve-sounds
  [already-loaded sounds node
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


(defn- retry
  [{:strs [retry welcome] :as node}
   {:keys [retries] :as context}]
  (let [sounds (if (> retries 1) (drop retry welcome) welcome)]
    (resolve-sounds [] sounds node context)))


(defmethod node/enter-type "dtmfcatch"
  [node {:keys [call deps] :as context}]
  (let [update-action-data (node-set/apply-preset node call)
        updated-call (or (get update-action-data :ivr.call/action-data) call)
        result (retry node {:deps deps :call updated-call :retries 1})]
    (merge update-action-data result)))


(defn sound-name-success
  [deps {:keys [call node retries loaded rest sound-url]}]
  (resolve-sounds (conj loaded sound-url) rest node
                  {:deps deps :call call :retries retries}))

(node/reg-action
  ::sound-name-success
  sound-name-success)


(defn- validate-digits-pattern [pattern digits]
  (let [regex (re-pattern pattern)]
    (every? #(re-find regex %) digits)))


(defn- digits-valid?
  [{:strs [finishonkey numdigits validationpattern] :as node}
   {:strs [digits termdigit] :as params}]
  (let [num-digits-ok? (or (nil? numdigits) (= numdigits (count digits)))
        term-digit-ok? (or (nil? finishonkey) (= finishonkey termdigit))
        pattern-ok? (or (nil? validationpattern) (nil? digits)
                        (validate-digits-pattern validationpattern digits))]
    (log "debug" "digits-valid?" {:num-digits-ok? num-digits-ok?
                                  :term-digit-ok? term-digit-ok?
                                  :pattern-ok? pattern-ok?})
    (and num-digits-ok? term-digit-ok? pattern-ok?)))


(defn- save-action-data
  [{:strs [varname] :as node}
   {:strs [digits] :as params}
   {:keys [action-data] :as call}]
  (let [set-digits [(node-set/->SetEntry digits varname)]
        set-success (get-in node ["case" "dtmf_ok" "set"])
        set (concat set-digits set-success)]
    (node-set/apply-set set call)))


(defn- leave-success
  [node {:keys [call deps params] :as context}]
  (let [update-action-data (save-action-data node params call)
        next (get-in node ["case" "dtmf_ok" "next"])
        result (node/go-to-next (assoc node "next" next) deps)]
    (merge update-action-data result)))


(defn- leave-max-attempts-reached
  [node {:keys [deps]}]
  (log "debug" "leave-max-attempts-reached")
  (let [next (get-in node ["case" "max_attempt_reached"])]
    (node/go-to-next (assoc node "next" next) deps)))


(defn- leave-retry
  [{:strs [max_attempts] :as node}
   {:keys [call deps params] :as context}]
  (let [retries (int (get params "retries"))]
    (if (<= max_attempts retries)
      (leave-max-attempts-reached node context)
      (retry node {:call call :deps deps :retries (inc retries)}))))


(defmethod node/leave-type "dtmfcatch"
  [node {:keys [params] :as context}]
  (if (digits-valid? node params)
    (leave-success node context)
    (leave-retry node context)))
