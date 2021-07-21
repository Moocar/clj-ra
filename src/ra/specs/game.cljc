(ns ra.specs.game
  (:require [clojure.spec.alpha :as s]
            [ra.specs.epoch :as epoch]
            [ra.specs.player :as player]
            [ra.specs :as rs]))

(s/def ::id nat-int?)
(s/def ::current-epoch (s/keys :req [::epoch/number]))
(s/def ::started-at rs/zoned-datetime?)
(s/def ::players (s/coll-of (s/keys :req [::player/id])))

(def sun-disk-sets
  {2 [#{9 6 5 2}
      #{8 7 4 3}]
   3 [#{13 8 5 2}
      #{12 9 6 3}
      #{11 10 7 4}]
   4 [#{13 6 2}
      #{12 7 3}
      #{11 8 4}
      #{10 9 5}]
   5 [#{16 7 2}
      #{15 8 3}
      #{14 9 4}
      #{13 10 5}
      #{12 11 6}]})

(def ras-per-epoch
  {2 6
   3 8
   4 9
   5 10})

(defn highest-sun-disk [game]
  (let [sun-disk-set (get sun-disk-sets (count (::players game)))]
    (last (sort (map #(apply max %) sun-disk-set)))))

(defn players [game]
  (::players game))

(defn player-count [game]
  (count (::players game)))
