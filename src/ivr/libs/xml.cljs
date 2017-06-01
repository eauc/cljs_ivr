(ns ivr.libs.xml
  (:require [hiccups.runtime :as hiccupsrt]
            [ivr.libs.logger :as logger])
  (:require-macros [hiccups.core :as hiccup :refer [html]]))


(defn- clj->xml [clj-data]
  (->> (html clj-data)
       (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n")
       (logger/default "silly" "clj->xml")))
