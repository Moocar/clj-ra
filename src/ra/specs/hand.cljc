(ns ra.specs.hand
  (:require [clojure.spec.alpha :as s]
            [ra.specs :as rs]
            [ra.specs.tile :as tile]
            [ra.specs.player :as player]
            [clojure.set :as set]))

(s/def ::tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::sun-disks (s/coll-of rs/sun-disk))
(s/def ::player (s/keys :req [::player/id]))
(s/def ::seat nat-int?)

(defn all-sun-disks
  "Returns the complete set of sun disks. Used and available"
  [hand]
  (set
   (concat (::used-sun-disks hand)
           (::available-sun-disks hand))))

(defn highest-sun-disk
  "Returns the highest sun disk in the hand"
  [hand]
  (last (sort (all-sun-disks hand))))

(defn disaster-tiles
  "Returns all the disaster tiles in the hand"
  [hand]
  (set (filter ::tile/disaster? (::tiles hand))))

(defn disaster-candidates
  "Returns the candidates for discard. E.g If there is an earthquake in
  the hand, it will return all monuments"
  [hand]
  (set (filter (fn [tile]
                 (and (not (::tile/disaster? tile))
                      ((set (map ::tile/type (disaster-tiles hand))) (::tile/type tile))))
               (::tiles hand))))

(defn check-selected-disaster-tiles
  [hand selected-tiles]
  (let [disaster-tiles (disaster-tiles hand)]
    (when-let [drought-tiles (seq (filter tile/drought? disaster-tiles))]
      (let [flood-tiles-in-hand  (filter tile/flood? (::tiles hand))
            flood-tiles-selected (filter tile/flood? selected-tiles)
            needed               (min (count flood-tiles-in-hand) (* (count drought-tiles) 2))]
        (when (< (count flood-tiles-selected) needed)
          (throw (ex-info "Need to select more flood tiles" {})))))

    (let [possible-tiles       (disaster-candidates hand)
          disaster-type->count (->> disaster-tiles
                                    (group-by ::tile/type)
                                    (reduce-kv (fn [a k v]
                                                 (assoc a k (count v)))
                                               {}))]
      (doseq [[disaster-type disaster-count] disaster-type->count]
        (let [candidates     (set (filter #(= disaster-type (::tile/type %)) possible-tiles))
              expected-count (min (count candidates) (* disaster-count 2))
              selected       (set (filter #(= disaster-type (::tile/type %)) selected-tiles))]
          (when (not= expected-count (count selected))
            (throw (ex-info "Invalid number of disaster tiles to discard"
                            {:expected-count expected-count
                             :received       (count selected)})))
          (when-not (set/subset? selected candidates)
            (throw (ex-info "Invalid selected disaster tiles" {}))))))))

(defn selected-disaster-tiles-valid?
  [hand selected-tiles]
  (try
    (check-selected-disaster-tiles hand selected-tiles)
    true
    (catch #?(:clj Exception) #?(:cljs :default) _ false))
  )
