(ns ra.app.hand
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [ra.app.player :as ui-player]
            [ra.app.sun-disk :as ui-sun-disk]
            [ra.app.tile :as ui-tile]
            [ra.app.ui :as ui]
            [ra.model.game :as m-game]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.specs.auction.reason :as auction-reason]
            [ra.specs.epoch :as epoch]
            [ra.specs.hand :as hand]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]))

(defn all-passes? [{:keys [::auction/bids]}]
  (empty? (filter ::bid/sun-disk bids)))

(defn can-pass? [{:keys [::auction/ra-hand ::auction/reason ::auction/tiles-full?] :as auction} hand]
  (or (not= (::hand/id ra-hand) (::hand/id hand))
      (not= reason ::auction-reason/invoke)
      (not (all-passes? auction))
      tiles-full?))

(defn auction-tiles-full? [epoch]
  (= 8 (count (::epoch/auction-tiles epoch))))

(defn disaster-candidate? [disaster-types tile]
  (and (not (::tile/disaster? tile))
       (disaster-types (::tile/type tile))))

(defn ui-info [props]
  (dom/div
      (ui-player/ui-player (::hand/player props)) " - "
      (str "seat: " (::hand/seat props)) " - "
      (str "score: " (::hand/score props))))

(defn ui-sun-disks [props {:keys [onClickSunDisk highest-bid auction]}]
  (let [discard-disaster-tiles? (seq (filter ::tile/disaster? (::hand/tiles props)))
        my-go? (and (::hand/my-go? props)  (not discard-disaster-tiles?))]
    (dom/div :.flex.space-x-2 {}
            (concat
             (map (fn [sun-disk]
                    (ui-sun-disk/ui {:value sun-disk
                                     :used? true}))
                  (::hand/used-sun-disks props))
             (map (fn [sun-disk]
                    (ui-sun-disk/ui (cond-> {:value sun-disk}
                                      (and my-go? onClickSunDisk (> sun-disk highest-bid) )
                                      (assoc :onClick #(onClickSunDisk sun-disk))
                                      (and my-go? (< sun-disk highest-bid))
                                      (assoc :too-low? true))))
                  (::hand/available-sun-disks props))
             (when (and onClickSunDisk my-go? (can-pass? auction props))
               [(ui-sun-disk/ui {:onClick #(onClickSunDisk nil)
                                 :value   "Pass"})])))))

(defn ui-actions [this props {:keys [epoch auction]}]
  (let [discard-disaster-tiles? (seq (filter ::tile/disaster? (::hand/tiles props)))
        my-go?                  (and (::hand/my-go? props)  (not discard-disaster-tiles?))]
    (dom/div :.flex.flex-col.space-y-2.pr-2.w-48
      (if discard-disaster-tiles?
        (ui/button {:onClick (fn []
                               (comp/transact! this [(m-game/discard-disaster-tiles
                                                      {::hand/id (::hand/id props)
                                                       :tile-ids (map ::tile/id (filter :ui/selected? (::hand/tiles props)))})]))}
          "Discard disaster tiles")
        (when (and my-go? (not auction) (not (::epoch/in-disaster? epoch)))
          (when-not (auction-tiles-full? epoch)
            (comp/fragment
             (ui/button {:onClick (fn []
                                    (comp/transact! this [(m-game/draw-tile {::hand/id (::hand/id props)})]))}
               "Draw Tile")
             (ui/button {:onClick (fn []
                                    (comp/transact! this [(m-game/invoke-ra {::hand/id (::hand/id props)})]))}
               "Invoke Ra"))))))))

(defn ui-tiles [props {:keys [click-god-tile]}]
  (let [discard-disaster-tiles? (seq (filter ::tile/disaster? (::hand/tiles props)))
        disaster-types (set (map ::tile/type (filter ::tile/disaster? (::hand/tiles props))))]
   (ui-tile/ui-tiles (map (fn [tile]
                            (comp/computed tile
                                           (if discard-disaster-tiles?
                                             (if (and (not (::tile/disaster? tile))
                                                      (disaster-types (::tile/type tile)))
                                               {:selectable? true}
                                               {:dimmed? true})
                                             (if (= ::tile-type/god (::tile/type tile))
                                               {:selectable? true
                                                :on-click    (fn [tile] (click-god-tile props tile))}
                                               (::hand/tiles props)))))
                          (::hand/tiles props)))))

(defsc Hand [this props {:keys [epoch] :as computed}]
  {:query [::hand/available-sun-disks
           ::hand/used-sun-disks
           ::hand/my-go?
           ::hand/seat
           ::hand/score
           ::hand/id
           {::hand/tiles (comp/get-query ui-tile/Tile)}
           {::hand/player (comp/get-query ui-player/Player)}]
   :ident ::hand/id}
  (dom/div :.h-48.relative.border-2.p-2
    (if (= (::hand/seat props) (::hand/seat (::epoch/current-hand epoch)))
      {:classes ["border-red-500"]}
      {:classes []})
    (dom/div :.absolute.top-0.right-2 {}
             (ui-info props)
             (ui-sun-disks props computed)
             (ui-actions this props epoch))
    (ui-tiles props computed)))

(def ui-hand (comp/factory Hand {:keyfn ::hand/seat}))
