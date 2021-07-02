(ns ra.app.epoch
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.app.auction :as ui-auction]
            [ra.app.hand :as ui-hand]
            [ra.app.sun-disk :as ui-sun-disk]
            [ra.app.tile :as ui-tile]
            [ra.model.game :as m-game]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.specs.epoch :as epoch]
            [ra.specs.hand :as hand]
            [ra.specs.tile :as tile]
            [ra.specs.player :as player]))

(def players->ra-count
  {2 6
   3 8
   4 9
   5 10})

(defn fill-blank-ra-spots [auction-tiles]
  (->> auction-tiles
       (count)
       (- 8)
       (range)
       (map (fn [_] (ui-tile/ui-tile {})))))

(defn ui-ra-track [props]
  (let [hand-count (count (::epoch/hands props))
        ra-count (count (::epoch/ra-tiles props))
        blank-spots (- (players->ra-count hand-count)
                       ra-count)]
    (dom/div :.inline-flex {}
             (dom/div :.border-2.rounded-md.flex.space-x-2.p-2 {}
                      (concat (map ui-tile/ui-tile (::epoch/ra-tiles props))
                              (map (fn [_] (ui-tile/ui-tile {})) (range blank-spots)))))))

(defn swap-god-tile [this props tile]
  (m/set-value! this :ui/selected-god-tile nil)
  (comp/transact! this [(m-game/use-god-tile {:god-tile-id (::tile/id (:ui/selected-god-tile props))
                                              ::hand/id (get-in props [:ui/selected-god-tile ::tile/hand ::hand/id])
                                              :auction-track-tile-id (::tile/id tile)})]))

(defn ui-auction-track [this props]
  (dom/div {}
    (dom/div :.border-2.rounded-md.inline-flex.space-x-2.p-2 {}
             (concat (->> (::epoch/auction-tiles props)
                          (sort-by ::tile/auction-track-position)
                          (map (fn [tile]
                                 (ui-tile/ui-tile (comp/computed tile (cond-> {}
                                                                        (:ui/selected-god-tile props)
                                                                        (assoc :on-click #(swap-god-tile this props %))))))))
                     (fill-blank-ra-spots (::epoch/auction-tiles props))))))

(defn highest-bid [auction]
  (reduce (fn [highest bid]
            (if (< (or (::bid/sun-disk highest) 0) (::bid/sun-disk bid))
              bid
              highest))
          {::bid/sun-disk 0}
          (::auction/bids auction)))

(m/defmutation select-god-tile [{:keys [hand tile]}]
  (action [env]
    (let [hand-ident [::hand/id (::hand/id hand)]
          tile-ident [::tile/id (::tile/id tile)]]
     (swap! (:state env)
            (fn [s]
              (-> s
                  (assoc-in (conj (:ref env) :ui/selected-god-tile) tile-ident)
                  (assoc-in (conj tile-ident ::tile/hand) hand-ident)))))))

(defn ui-hands [this props]
  (dom/div {}
    (->> (concat (::epoch/hands props) (::epoch/hands props))
         (drop-while (fn [hand]
                       (not= (::player/id (::hand/player hand))
                             (::player/id (:ui/current-player props)))))
         (take (count (::epoch/hands props)))
         (map (fn [hand]
                (ui-hand/ui-hand
                 (if (::epoch/auction props)
                   (comp/computed hand {:onClickSunDisk (fn [sun-disk]
                                                          (comp/transact! this [(m-game/bid {::hand/id (::hand/id hand) :sun-disk sun-disk})]))
                                        :highest-bid    (highest-bid (::epoch/auction props))
                                        :epoch          props
                                        :auction        (::epoch/auction props)})
                   (comp/computed hand {:epoch          props
                                        :click-god-tile (fn [hand tile]
                                                          (if (:ui/selected-god-tile props)
                                                            (m/set-value! this :ui/selected-god-tile nil)
                                                            (comp/transact! this [(select-god-tile {:hand hand
                                                                                                    :tile tile})])))}))))))))

(defn auction-tiles-full? [epoch]
  (= 8 (count (::epoch/auction-tiles epoch))))

(defn ui-tile-bag [this props]
  (dom/div :.flex.justify-center.items-center.w-24.h-24.rounded-md.bg-green-300.opacity-50
    (if (and (= (::player/id (::hand/player (::epoch/current-hand props)))
                (::player/id (:ui/current-player props)))
             (not (::epoch/auction props))
             (not (::epoch/in-disaster? props))
             (not (auction-tiles-full? props)))
      {:onClick (fn []
                  (comp/transact! this [(m-game/draw-tile {::hand/id (::hand/id (::epoch/current-hand props))})]))
       :classes ["cursor-pointer" "hover:bg-green-500" "opacity-100"]}
      {})
    "Tile Bag"))

(defsc Epoch [this props]
  {:query [::epoch/current-sun-disk
           ::epoch/number
           ::epoch/id
           {[:ui/current-player '_] [::player/id]}
           {::epoch/auction (comp/get-query ui-auction/Auction)}
           {:ui/selected-god-tile [::tile/id {::tile/hand [::hand/id]}]}
           ::epoch/in-disaster?
           {::epoch/current-hand [::hand/seat {::hand/player [::player/id]} ::hand/id]}
           {::epoch/ra-tiles (comp/get-query ui-tile/Tile)}
           {::epoch/auction-tiles (comp/get-query ui-tile/Tile)}
           {::epoch/hands (comp/get-query ui-hand/Hand)}]
   :ident ::epoch/id}
  (dom/div :.flex.flex-col.content-center {}
           (dom/strong "Ra Track")
           (dom/div :.inline-flex.items-center {}
                    (ui-ra-track props))
    (dom/strong "Auction track")
    (dom/div :.flex.flex-row.justify-between {}
             (ui-auction-track this props)
             (ui-tile-bag this props))
    (dom/div {}
      (dom/h3 :.font-bold.text-xl "Seats")
      (dom/div {}
        (ui-hands this props)))))

(def ui-epoch (comp/factory Epoch {:keyfn ::epoch/number}))
