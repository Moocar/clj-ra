(ns ra.app.epoch
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.semantic-ui.elements.segment.ui-segment
             :refer
             [ui-segment]]
            [com.fulcrologic.semantic-ui.elements.segment.ui-segment-group
             :refer
             [ui-segment-group]]
            [com.fulcrologic.semantic-ui.views.card.ui-card-group
             :refer
             [ui-card-group]]
            [ra.app.auction :as ui-auction]
            [ra.app.hand :as ui-hand]
            [ra.app.player :as ui-player]
            [ra.app.tile :as ui-tile]
            [ra.specs.epoch :as epoch]
            [ra.specs.hand :as hand]
            [ra.specs.tile :as tile]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.model.game :as m-game]
            [com.fulcrologic.fulcro.mutations :as m]))

(def players->ra-count
  {2 6
   3 8
   4 9
   5 10})

(defn ui-ra-track [props]
  (let [hand-count (count (::epoch/hands props))
        ra-count (count (::epoch/ra-tiles props))
        blank-spots (- (players->ra-count hand-count)
                       ra-count)]
   (ui-segment {:compact true}
     (dom/strong "Ra track")
     (ui-card-group {} (concat (map ui-tile/ui-tile (::epoch/ra-tiles props))
                               (map (fn [_] (ui-tile/ui-blank-ra-spot)) (range blank-spots)))))))

(defn fill-blank-ra-spots [auction-tiles]
  (->> auction-tiles
       (count)
       (- 8)
       (range)
       (map (fn [_] (ui-tile/ui-blank-ra-spot)))))

(defn swap-god-tile [this props tile]
  (comp/transact! this [(m-game/use-god-tile {:god-tile-id (::tile/id (:ui/selected-god-tile props))
                                              :auction-track-tile-id (::tile/id tile)})]))

(defn ui-auction-track [this props]
  (ui-segment {:compact true}
    (dom/strong "Auction track")
    (ui-card-group {} (concat (->> (::epoch/auction-tiles props)
                                   (sort-by ::tile/auction-track-position)
                                   (map (fn [tile]
                                          (ui-tile/ui-tile (comp/computed tile (cond-> {}
                                                                                 (:ui/selected-god-tile props)
                                                                                 (assoc :on-click #(swap-god-tile this props %))))))))
                              (fill-blank-ra-spots (::epoch/auction-tiles props))))))

(defn highest-bid [{:keys [::auction/bids]}]
  (apply max (or (seq (map ::bid/sun-disk bids)) [0])))

(defsc Epoch [this {:keys [::epoch/number
                           ::epoch/current-sun-disk
                           ::epoch/auction
                           ::epoch/hands]
                    :as   props}]
  {:query [::epoch/current-sun-disk
           ::epoch/number
           ::epoch/id
           {::epoch/auction (comp/get-query ui-auction/Auction)}
           :ui/selected-god-tile
           ::epoch/in-disaster?
           {::epoch/current-hand [::hand/seat]}
           {::epoch/ra-tiles (comp/get-query ui-tile/Tile)}
           {::epoch/auction-tiles (comp/get-query ui-tile/Tile)}
           {::epoch/hands (comp/get-query ui-hand/Hand)}]
   :ident ::epoch/id}
  (dom/div {}
    (dom/p (str "Epoch: " number))
    (ui-ra-track props)
    (ui-segment {:compact "true"}
      (ui-tile/ui-sun-disk {:value current-sun-disk}))
    (ui-auction-track this props)
    (when auction
      (ui-auction/ui-auction auction))
    (ui-segment {:compact true}
                (dom/h3 "Seats")
                (ui-segment-group {}
                                  (map (fn [hand]
                                         (ui-hand/ui-hand
                                          (if auction
                                            (comp/computed hand {:onClickSunDisk (fn [sun-disk]
                                                                                   (js/console.log "sun disk clicked" sun-disk)
                                                                                   (comp/transact! this [(m-game/bid {::hand/id (::hand/id hand) :sun-disk sun-disk})]))
                                                                 :highest-bid    (highest-bid auction)
                                                                 :epoch          props
                                                                 :auction        auction})
                                            (comp/computed hand {:epoch props
                                                                 :click-god-tile (fn [tile]
                                                                                   (if (:ui/selected-god-tile props)
                                                                                     (m/set-value! this :ui/selected-god-tile nil)
                                                                                     (m/set-value! this :ui/selected-god-tile tile)))}))))
                      hands)))))

(def ui-epoch (comp/factory Epoch {:keyfn ::epoch/number}))
