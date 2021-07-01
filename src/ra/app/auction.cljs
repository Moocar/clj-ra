(ns ra.app.auction
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [ra.app.sun-disk :as ui-sun-disk]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]))

(defsc Auction [_ {:keys [::auction/bids]}]
  {:query [::auction/reason
           {::auction/ra-hand [::hand/id]}
           ::auction/tiles-full?
           {::auction/bids [{::bid/hand [{::hand/player [::player/name]}]}
                            ::bid/sun-disk]}]}
  (dom/div {:compact true}
    (dom/h3 "bids")
    (dom/div {}
      (map (fn [{:keys [::bid/hand ::bid/sun-disk]}]
             (dom/span {}
                       (ui-sun-disk/ui {:value sun-disk})
                       (get-in hand [::hand/player ::player/name])))
           bids))))

(def ui-auction (comp/factory Auction))
