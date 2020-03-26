(ns ra.date
  (:require [clojure.string :as str]
            [cognitect.transit :as cljs-transit]))

(defn zdt->js-date [zdt]
  (first (.-rep zdt)))

(defn now-zdt []
  (cljs-transit/tagged-value "zdt" [(js/Date.) nil]))

(defn DD-MM-YYYY->zdt [s]
  (if (str/blank? (str/trim (or s "")))
    nil
    (let [[day month year] (str/split s #"-")
          yyyy-mm-dd (str year "-" month "-" day)]
      (cljs-transit/tagged-value "zdt" [(js/Date. (.parse js/Date yyyy-mm-dd)) nil]))))

(defn zdt->DD-MM-YYYY [d]
  (if d
    (let [d (zdt->js-date d)]
      (str (.getDate d) "-" (inc (.getMonth d)) "-" (.getFullYear d)))
    ""))
