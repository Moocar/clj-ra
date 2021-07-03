(ns ra.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn re-spec [regex]
  (s/and string? #(re-matches regex %)))

(def words
  #{"desire" "meddle" "sleep" "mess" "up" "land" "invent" "cute"
    "pie" "yawn" "various" "odd" "zesty" "look" "sad" "shock"
    "mitten" "sick" "wasteful" "fumbling" "encouraging" "jobless"})

(def non-empty-string?
  (s/and string? (complement str/blank?)))

(defn zoned-datetime? [s]
  #?(:clj (instance? java.time.ZonedDateTime s)
     :cljs non-empty-string?))

(defn calendar? [c]
  #?(:clj (instance? java.util.Calendar c)
     :cljs non-empty-string?))

(def sun-disk
  #{1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16})
