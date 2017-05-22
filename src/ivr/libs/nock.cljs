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
                                          :soundname "son1"}}}))
        (.get "/account/0007/file")
        (.query true)
        (.reply 200 (clj->js {:meta {:total_count 1}
                              :objects [{:_id "id_son1"}]})))))
