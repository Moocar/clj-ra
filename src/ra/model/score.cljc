(ns ra.model.score
  (:require [ra.specs.tile :as tile]
            [ra.specs.hand :as hand]
            [ra.specs.tile.type :as tile-type]
            [ra.specs.epoch-hand :as epoch-hand]))

(defn tally-hand [hand-score]
  (+ (reduce + (vals (:tile-scores hand-score)))
     (or (:sun-disk-scores hand-score) 0)))

(defn count-sun-disks [epoch-hand]
  (reduce + (concat (::hand/available-sun-disks (::epoch-hand/hand epoch-hand))
                    (::hand/used-sun-disks (::epoch-hand/hand epoch-hand)))))

(defn score-epoch [epoch-hands]
  (let [sort-by-pharoah-count   (sort
                                 (map (fn [epoch-hand]
                                        (count (filter tile/pharoah? (::epoch-hand/tiles epoch-hand))))
                                      epoch-hands))
        most-pharoahs           (last sort-by-pharoah-count)
        least-pharoas           (first sort-by-pharoah-count)
        sun-disk-totals         (sort (map count-sun-disks epoch-hands))
        lowest-sun-disks-total  (first sun-disk-totals)
        highest-sun-disks-total (last sun-disk-totals)]
    (map (fn [epoch-hand]
           (let [tiles (::epoch-hand/tiles epoch-hand)]
             (cond-> {:hand (::epoch-hand/hand epoch-hand)
                      :epoch (::epoch-hand/epoch epoch-hand)
                      :tile-scores (cond-> {::tile-type/river
                                            (let [flood-count (count (filter tile/flood? tiles))
                                                  nile-count  (count (filter tile/nile? tiles))]
                                              (if (pos? flood-count)
                                                (+ flood-count nile-count)
                                                0))

                                            ::tile-type/god
                                            (* 2 (count (filter tile/god? tiles)))

                                            ::tile-type/gold
                                            (* 3 (count (filter tile/gold? tiles)))

                                            ::tile-type/civilization
                                            (let [unique-civs-count (count (distinct (map ::tile/civilization-type (filter tile/civ? tiles))))]
                                              (case unique-civs-count
                                                0 -5
                                                1 0
                                                2 0
                                                3 5
                                                4 10
                                                5 15))

                                            ::tile-type/pharoah (let [pharoah-count (count (filter tile/pharoah? tiles))]
                                                                  (cond (= pharoah-count most-pharoahs) 5
                                                                        (= pharoah-count least-pharoas) -2
                                                                        :else 0))})}
               ;; last epoch
               (= (::epoch-hand/epoch epoch-hand) 3)
               (-> (assoc-in [:tile-scores ::tile-type/monument]
                             (let [monument-groups (group-by ::tile/monument-type (filter tile/monument? tiles))]
                               (+ (case (count monument-groups)
                                    7 10
                                    8 15
                                    (count monument-groups))
                                  (->> monument-groups
                                       (map (fn [[_ tiles]]
                                              (case (count tiles)
                                                3 5
                                                4 10
                                                5 15
                                                0)))
                                       (reduce + 0)))))
                   (assoc :sun-disk-scores (condp = (count-sun-disks epoch-hand)
                                             lowest-sun-disks-total  -5
                                             highest-sun-disks-total 5
                                             0))))))
         epoch-hands)))

(defn order-hands-winning [hands]
  (->> hands
       (sort (fn [a b]
               (if (= (::hand/score a) (::hand/score b))
                 (compare (hand/highest-sun-disk a)
                          (hand/highest-sun-disk b))
                 (compare (::hand/score a)
                          (::hand/score b)))))
       reverse))
