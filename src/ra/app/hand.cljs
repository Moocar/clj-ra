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
            [ra.specs.tile.type :as tile-type]
            [ra.specs.player :as player]))

(defn all-passes? [{:keys [::auction/bids]}]
  (empty? (filter ::bid/sun-disk bids)))

(defn can-pass? [{:keys [::auction/ra-hand ::auction/reason ::auction/tiles-full?] :as auction} hand]
  (or (not= (::hand/id ra-hand) (::hand/id hand))
      (not= reason ::auction-reason/invoke)
      (not (all-passes? auction))
      tiles-full?))

(defn disaster-candidate? [disaster-types tile]
  (and (not (::tile/disaster? tile))
       (disaster-types (::tile/type tile))))

(defn ui-info [props]
  (dom/div
      (ui-player/ui-player (::hand/player props)) " - "
      (str "seat: " (::hand/seat props)) " - "
      (str "score: " (::hand/score props))))

(defn my-go? [hand epoch]
  (and (::hand/my-go? hand)
       (= (::player/id (::hand/player hand))
          (::player/id (:ui/current-player epoch)))))

(defn ui-sun-disks [props {:keys [onClickSunDisk highest-bid auction epoch]}]
  (dom/div :.flex.space-x-2 {}
           (concat
            (map (fn [sun-disk]
                   (let [used? ((set (::hand/used-sun-disks props)) sun-disk)]
                     (ui-sun-disk/ui (cond-> {:value sun-disk}
                                       (and (my-go? props epoch) auction (< (::bid/sun-disk highest-bid) sun-disk) (not used?))
                                       (assoc :onClick #(onClickSunDisk sun-disk))
                                       (and (my-go? props epoch) auction (< sun-disk (::bid/sun-disk highest-bid)) (not used?))
                                       (assoc :too-low? true)
                                       used?
                                       (assoc :used? true)))))
                 (concat (::hand/used-sun-disks props)
                         (::hand/available-sun-disks props)))
            (when (and (my-go? props epoch) auction)
              [(ui-sun-disk/ui-pass {:onClick #(onClickSunDisk nil)})]))))

(defn ui-current-bid [props {:keys [auction]}]
  (let [auction-bid (first (filter (fn [bid]
                                     (= (::hand/id (::bid/hand bid))
                                        (::hand/id props)))
                                   (::auction/bids auction)))]
    (when auction-bid
      (ui-sun-disk/ui-large {:value (or (::bid/sun-disk auction-bid)
                                        "Pass")}))))

(defn ui-actions [this props]
  (let [discard-disaster-tiles? (seq (filter ::tile/disaster? (::hand/tiles props)))]
    (dom/div :.flex.flex-col.space-y-2.pr-2.w-48
      (when discard-disaster-tiles?
        (ui/button {:onClick (fn []
                               (comp/transact! this [(m-game/discard-disaster-tiles
                                                      {::hand/id (::hand/id props)
                                                       :tile-ids (map ::tile/id (filter :ui/selected? (::hand/tiles props)))})]))}
          "Discard disaster tiles")))))

(defn ui-tiles [props {:keys [click-god-tile]}]
  (let [discard-disaster-tiles? (seq (filter ::tile/disaster? (::hand/tiles props)))
        disaster-types (set (map ::tile/type (filter ::tile/disaster? (::hand/tiles props))))]
    (->> (::hand/tiles props)
         (group-by ::tile/title)
         (map (fn [[_ [tile :as tiles]]]
                (comp/computed tile
                               (merge {:stack-size (count tiles)}
                                      (if discard-disaster-tiles?
                                        (if (and (not (::tile/disaster? tile))
                                                 (disaster-types (::tile/type tile)))
                                          {:selectable? true}
                                          {:dimmed? true})
                                        (if (= ::tile-type/god (::tile/type tile))
                                          {:selectable? true
                                           :on-click    (fn [tile] (click-god-tile props tile))}
                                          (::hand/tiles props)))))))
         (sort-by (juxt ::tile/type ::tile/title))
         (ui-tile/ui-tiles))))

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
  (dom/div :.h-48.border-2.p-2.flex.flex-row.justify-between
    (if (= (::hand/seat props) (::hand/seat (::epoch/current-hand epoch)))
      {:classes ["border-red-500"]}
      {:classes []})
    (dom/div  {}
             (ui-tiles props computed))
    (dom/div  {}
             (ui-info props)
             (ui-sun-disks props computed)
             (ui-current-bid props computed)
             (ui-actions this props))))

(def ui-hand (comp/factory Hand {:keyfn ::hand/seat}))
