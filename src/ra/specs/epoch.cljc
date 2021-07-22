(ns ra.specs.epoch
  (:require [clojure.spec.alpha :as s]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.specs :as rs]
            [ra.specs.hand :as hand]
            [ra.specs.auction :as auction]))

(s/def ::current-sun-disk rs/sun-disk)
(s/def ::number #{1 2 3})
(s/def ::ra-tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::auction-tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::last-ra-invokee (s/keys :req [::player/id]))
(s/def ::player-hands (s/coll-of (s/keys :req [::player/id])))

(defn auction-tiles-full?
  "Returns true if the auction track is full"
  [epoch]
  (= 8 (count (::auction-tiles epoch))))

(defn active-bidder-count
  "Returns the number of hands that can still bid"
  [epoch]
  (count (filter (fn [hand]
                   (seq (::hand/available-sun-disks hand)))
                 (::hands epoch))))

(defn bid-count [epoch]
  (count (::auction/bids (::auction epoch))))
