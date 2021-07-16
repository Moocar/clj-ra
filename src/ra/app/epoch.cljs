(ns ra.app.epoch
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.app.auction :as ui-auction]
            [ra.app.hand :as ui-hand]
            [ra.app.tile :as ui-tile]
            [ra.model.game :as m-game]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.specs.epoch :as epoch]
            [ra.specs.hand :as hand]
            [ra.specs.tile :as tile]
            [ra.specs.player :as player]
            [ra.specs.tile.type :as tile-type]))

(defn swap-god-tile [this props tile]
  (m/set-value! this :ui/selected-god-tile nil)
  (comp/transact! this [(m-game/use-god-tile {:god-tile-id (::tile/id (:ui/selected-god-tile props))
                                              ::hand/id (get-in props [:ui/selected-god-tile ::tile/hand ::hand/id])
                                              :auction-track-tile-id (::tile/id tile)})]))

(defn fill-blank-ra-spots [auction-tiles]
  (->> auction-tiles
       (count)
       (- 8)
       (range)
       (map (fn [_] (ui-tile/ui-tile {})))))

(defn ui-auction-track [this {:keys [epoch]}]
  (dom/div :.flex.flex-row.flex-wrap.gap-2 {}
    (concat (->> (::epoch/auction-tiles epoch)
                 (sort-by ::tile/auction-track-position)
                 (map (fn [tile]
                        (dom/div
                          {:style {"animation-name"     "drawtile"
                                   "animation-duration" "1s"
                                   "transform"          "scale(1, 1)"}}
                          (ui-tile/ui-tile (comp/computed tile (cond-> {}
                                                                 (and (:ui/selected-god-tile epoch)
                                                                      (not (= ::tile-type/god (::tile/type tile))))
                                                                 (assoc :on-click #(swap-god-tile this epoch %)
                                                                        :selectable? true))))))))
            (fill-blank-ra-spots (::epoch/auction-tiles epoch)))))

(defn highest-bid [auction]
  (reduce (fn [highest bid]
            (if (< (or (::bid/sun-disk highest) 0) (::bid/sun-disk bid))
              bid
              highest))
          {::bid/sun-disk 0}
          (::auction/bids auction)))

(m/defmutation select-god-tile [{:keys [hand tile epoch]}]
  (action [env]
    (let [hand-ident [::hand/id (::hand/id hand)]
          tile-ident [::tile/id (::tile/id tile)]]
     (swap! (:state env)
            (fn [s]
              (-> s
                  (assoc-in (conj [::epoch/id (::epoch/id epoch)] :ui/selected-god-tile) tile-ident)
                  (assoc-in (conj tile-ident ::tile/hand) hand-ident)))))))

(defn auction-tiles-full? [epoch]
  (= 8 (count (::epoch/auction-tiles epoch))))

(defsc Epoch [_ _]
  {:query [::epoch/current-sun-disk
           ::epoch/number
           ::epoch/id
           {[:ui/current-player '_] [::player/id]}
           {::epoch/auction (comp/get-query ui-auction/Auction)}
           {:ui/selected-god-tile [::tile/id {::tile/hand [::hand/id]}]}
           ::epoch/in-disaster?
           {::epoch/last-ra-invoker [::hand/id]}
           {::epoch/current-hand (comp/get-query ui-hand/Hand)}
           {::epoch/ra-tiles (comp/get-query ui-tile/Tile)}
           {::epoch/auction-tiles (comp/get-query ui-tile/Tile)}
           {::epoch/hands (comp/get-query ui-hand/Hand)}]
   :ident ::epoch/id})
