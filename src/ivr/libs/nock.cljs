(ns ivr.libs.nock
  (:require [cljs.nodejs :as nodejs]
            [ivr.debug :refer [debug?]]))

(defonce nock (nodejs/require "nock"))

(defonce cloudstore
  (when debug?
    (-> (nock "http://clouddispatch/cloudstore")
        (.log js/console.log)
        (.persist)
        (.get "/account/0007/script/1234")
        (.reply 200 (clj->js {:id "1234"
                              :start "1"
                              :nodes {:1 {:type "announcement"
                                          :soundname "son1"
                                          :preset {:value "toto"
                                                   :varname "titi"}}
                                      :2 {:type "dtmfcatch"
                                          :finishonkey "4"
                                          :numdigits 3
                                          :preset {:value "+33478597106"
                                                   :varname "toto"}
                                          :retry 2
                                          :timeout "5"
                                          :welcome [{:varname "titi"
                                                     :voice "alice"}
                                                    {:soundname "son1"}
                                                    {:varname "toto"
                                                     :voice "alice"
                                                     :pronounce "phone"}]}}}))
        (.get "/account/0007/file")
        (.query true)
        (.reply 200 (clj->js {:meta {:total_count 1}
                              :objects [{:_id "id_son1"}]})))))
