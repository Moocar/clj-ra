(ns ra.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]))

(defn nums-gen [n]
  (gen/vector (gen/choose 0 9) n))

(defn num-str-gen [n]
  (gen/fmap str/join (nums-gen n)))

(defn re-spec [regex]
  (s/and string? #(re-matches regex %)))

(def words
  #{"desire" "meddle" "sleep" "mess" "up" "land" "invent" "cute"
    "pie" "yawn" "various" "odd" "zesty" "look" "sad" "shock"
    "mitten" "sick" "wasteful" "fumbling" "encouraging" "jobless"})

(def name-gen
  (gen/fmap #(str/join " " (map str/capitalize %))
            (gen/tuple (gen/elements words) (gen/elements words))))

#_(def non-empty-string-alphanumeric
  "Generator for non-empty alphanumeric strings"
  (gen/such-that #(not= "" %)
                 (gen/string-alphanumeric)))

(def non-empty-string?
  (s/and string? (complement str/blank?)))

#_(def email-gen
  "Generator for email addresses"
  (gen/fmap
   (fn [[name host tld]]
     (str name "@" host "." tld))
   (gen/tuple
    non-empty-string-alphanumeric
    non-empty-string-alphanumeric
    non-empty-string-alphanumeric)))

#_(s/def ::email
  (s/with-gen
    (re-spec #".+@.+\..+")
    (fn [] email-gen)))

(defn chan? [x]
  true
  #_(instance? #?(:clj clojure.core.async.impl.channels.ManyToManyChannel
                :cljs cljs.core.async.impl.channels.ManyToManyChannel)
             x))

(defn zoned-datetime? [s]
  #?(:clj (instance? java.time.ZonedDateTime s)
     :cljs non-empty-string?))

(defn calendar? [c]
  #?(:clj (instance? java.util.Calendar c)
     :cljs non-empty-string?))

(def sun-disk
  #{1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16})
