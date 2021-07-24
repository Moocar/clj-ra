(ns ra.specs.auction
  (:require [ra.specs.auction.bid :as bid]
            [ra.specs.hand :as hand]
            [ra.specs.auction.reason :as auction-reason]))

(defn highest-bid
  "Returns the highest bid in the auction"
  [auction]
  (reduce (fn [highest bid]
            (if (< (or (::bid/sun-disk highest) 0) (::bid/sun-disk bid))
              bid
              highest))
          {::bid/sun-disk 0}
          (::bids auction)))

(defn all-passes?
  "Returns true if all bids are passes"
  [{:keys [::bids]}]
  (empty? (filter ::bid/sun-disk bids)))

(defn can-pass?
  "Returns true if the hand can pass this auction. You can pass as long
  as you didn't voluntarily invoke ra and someone else passed"
  [{:keys [::ra-hand ::reason ::tiles-full?] :as auction} hand]
  (or (not= (::hand/id ra-hand) (::hand/id hand))
      (not= reason ::auction-reason/invoke)
      (not (all-passes? auction))
      tiles-full?))
