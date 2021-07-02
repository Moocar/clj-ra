(ns ra.app.auction
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]))

(defsc Auction [_ _]
  {:query [::auction/reason
           {::auction/ra-hand [::hand/id]}
           ::auction/tiles-full?
           {::auction/bids [{::bid/hand [::hand/id {::hand/player [::player/name]}]}
                            ::bid/sun-disk]}]})

(def ui-auction (comp/factory Auction))
