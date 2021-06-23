(ns ra.app.auction
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.semantic-ui.elements.segment.ui-segment
             :refer
             [ui-segment]]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.app.tile :as ui-tile]
            [com.fulcrologic.fulcro.dom :as dom]))

(defsc Auction [_ {:keys [::auction/bids]}]
  {:query [::auction/reason
           {::auction/ra-hand [::hand/id]}
           ::auction/tiles-full?
           {::auction/bids [{::bid/hand [{::hand/player [::player/name]}]}
                            ::bid/sun-disk]}]}
  (ui-segment {:compact true}
              (dom/h3 "bids")
              (dom/div {}
                (map (fn [{:keys [::bid/hand ::bid/sun-disk]}]
                       (dom/span {}
                         (ui-tile/ui-sun-disk {:value sun-disk})
                         (get-in hand [::hand/player ::player/name])))
                     bids))))

(def ui-auction (comp/factory Auction))
