(ns ra.specs.game
  (:require [clojure.spec.alpha :as s]
            [ra.specs.player :as player]
            [ra.specs :as rs]
            [ra.core :as core]
            [ra.specs.hand :as hand]
            [ra.specs.auction :as auction]
            [ra.specs.epoch-hand :as epoch-hand]))

(s/def ::id nat-int?)
(s/def ::epoch #{1 2 3})
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

(defn highest-sun-disk
  "The highest possible sun disk. E.g with 2 players, it's 9. With 3
  players it's 13."
  [game]
  (let [sun-disk-set (get sun-disk-sets (count (::players game)))]
    (last (sort (map #(apply max %) sun-disk-set)))))

(defn player-count
  [game]
  (count (::players game)))

(defn max-ras
  "The total size of the ra track"
  [game]
  (get ras-per-epoch (player-count game)))

(defn last-ra?
  "Is the current epoch waiting for the final ra? (e.g has 7 or 8 ras
  out)"
  [game]
  (= (inc (count (::ra-tiles game)))
     (max-ras game)))

(defn current-hand
  "Returns the current hand in the current epoch in the game"
  [game]
  (::current-hand game))

(defn new-short-id
  "Returns a short 4 character human readable game ID"
  []
  (apply str (repeatedly 4 core/rand-char)))

(defn auction-tiles-full?
  "Returns true if the auction track is full"
  [game]
  (= 8 (count (::auction-tiles game))))

(defn active-bidder-count
  "Returns the number of hands that can still bid"
  [game]
  (count (filter (fn [hand]
                   (seq (::hand/available-sun-disks hand)))
                 (::hands game))))

(defn bid-count [game]
  (count (::auction/bids (::auction game))))

(defn hand-with-highest-sun-disk
  [game]
  (last (sort-by hand/highest-sun-disk (::hands game))))

(defn highest-pharoah-count
  "Returns the highest number of pharoahs in a hand"
  [game]
  (last (sort (map hand/pharoah-count (::hands game)))))

(defn get-epoch-hands [game epoch]
  (filter #(= epoch (::epoch-hand/epoch %))
          (::epoch-hands game)))

(defn get-current-epoch-hands [game]
  (get-epoch-hands game (if (::finished-at game)
                          3
                          (dec (::epoch game)))))
