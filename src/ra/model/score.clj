(ns ra.model.score
  (:require [ra.model.tile :as m-tile]
            [ra.specs.hand :as hand]
            [ra.specs.tile.type :as tile-type]
            [ra.specs.tile :as tile]
            [ra.specs.epoch :as epoch]))

(defn count-sun-disks [hand]
  (reduce + (concat (::hand/available-sun-disks hand)
                    (::hand/used-sun-disks hand))))

(defn score-epoch [epoch]
  (assert (< (::epoch/number epoch) 3))
  (let [hands                 (::epoch/hands epoch)
        sort-by-pharoah-count (sort
                               (map (fn [hand]
                                      (count (filter m-tile/pharoah? (::hand/tiles hand))))
                                    hands))
        most-pharoahs         (last sort-by-pharoah-count)
        least-pharoas         (first sort-by-pharoah-count)
        sun-disk-totals       (sort (map count-sun-disks hands))]
    (map (fn [hand]
           (let [tiles (::hand/tiles hand)]
             (cond-> {::hand/id    (::hand/id hand)
                      :db/id       (:db/id hand)
                      :tile-scores (cond-> {::tile-type/river
                                            (let [flood-count (count (filter m-tile/flood? tiles))
                                                  nile-count  (count (filter m-tile/nile? tiles))]
                                              (if (pos? flood-count)
                                                (+ flood-count nile-count)
                                                0))

                                            ::tile-type/god
                                            (* 2 (count (filter m-tile/god? tiles)))

                                            ::tile-type/gold
                                            (* 3 (count (filter m-tile/gold? tiles)))

                                            ::tile-type/civilization
                                            (let [unique-civs-count (count (distinct (map ::tile/civilization-type (filter m-tile/civ? tiles))))]
                                              (case unique-civs-count
                                                0 -5
                                                1 0
                                                2 0
                                                3 5
                                                4 10
                                                5 15))

                                            ::tile-type/pharoah (let [pharoah-count (count (filter m-tile/pharoah? tiles))]
                                                                  (cond (= pharoah-count most-pharoahs) 5
                                                                        (= pharoah-count least-pharoas) -2
                                                                        :else 0))})}
               ;; last epoch
               (= (::epoch/number epoch) 2)
               (-> (assoc-in [:tile-scores ::tile-type/monument]
                             (let [monument-groups (group-by ::tile/monument-type (filter m-tile/monument? tiles))]
                               (+ (case (count monument-groups)
                                    7 10
                                    8 15
                                    (count monument-groups))
                                  (->> monument-groups
                                       (map (fn [group]
                                              (case (count group)
                                                3 5
                                                4 10
                                                5 15
                                                0)))
                                       (reduce + 0)))))
                   (assoc :sun-disks (+ (if (= (count-sun-disks hand)
                                               (first sun-disk-totals))
                                          -5
                                          0)
                                        (if (= (count-sun-disks hand)
                                               (last sun-disk-totals))
                                          5
                                          0)))))))
         hands)))

(defn tally-hand [hand-scores]
  (+ (reduce + (vals (:tile-scores hand-scores)))
     (or (:sun-disks hand-scores) 0)))
