(ns ra.specs.auction
  (:require [ra.specs.auction.bid :as bid]))

(defn highest-bid [auction]
  (reduce (fn [highest bid]
            (if (< (or (::bid/sun-disk highest) 0) (::bid/sun-disk bid))
              bid
              highest))
          {::bid/sun-disk 0}
          (::bids auction)))
