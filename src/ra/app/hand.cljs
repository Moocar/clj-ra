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

(defn ui-sun-disks [hand {:keys [onClickSunDisk my-go? highest-bid auction]}]
  (let [current-bid (first (filter (fn [bid]
                                     (= (::hand/id (::bid/hand bid))
                                        (::hand/id hand)))
                                   (::auction/bids auction)))]
    (dom/div :.flex.space-x-2.h-16 {}
      (concat
       (map (fn [sun-disk]
              (let [used? ((set (::hand/used-sun-disks hand)) sun-disk)]
                (ui-sun-disk/ui (cond-> {:value (or sun-disk "Pass")}
                                  (and my-go? (::hand/my-go? hand) auction (< (::bid/sun-disk highest-bid) sun-disk) (not used?))
                                  (assoc :onClick #(onClickSunDisk sun-disk))
                                  (and my-go? (::hand/my-go? hand) auction (< sun-disk (::bid/sun-disk highest-bid)) (not used?))
                                  (assoc :too-low? true)
                                  used?
                                  (assoc :used? true)
                                  (= (::bid/sun-disk current-bid) sun-disk)
                                  (assoc :large true)))))
            (concat (::hand/used-sun-disks hand)
                    (sort (fn [a b]
                            (if (nil? a)
                              1
                              (if (nil? b)
                                -1
                                (if (< a b)
                                  -1
                                  (if (= a b)
                                    0
                                    1)))))
                          (concat (::hand/available-sun-disks hand)
                                  (when current-bid
                                    [(::bid/sun-disk current-bid)])))))
       (when (and my-go? (::hand/my-go? hand) auction (can-pass? auction hand))
         [(ui-sun-disk/ui-pass {:onClick #(onClickSunDisk nil)})])))))

(defn ui-tiles [hand {:keys [click-god-tile my-go?]}]
  (let [tiles (::hand/tiles hand)
        discard-disaster-tiles? (seq (filter ::tile/disaster? tiles))
        disaster-types (set (map ::tile/type (filter ::tile/disaster? tiles)))]
    (if (and discard-disaster-tiles? my-go? (::hand/my-go? hand))
      (->> tiles
           (map (fn [tile]
                  (comp/computed tile
                                 (merge {}
                                        (if (and (not (::tile/disaster? tile))
                                                 (disaster-types (::tile/type tile)))
                                          {:selectable? true}
                                          {:dimmed? true})))))
           (sort-by (juxt ::tile/type ::tile/title))
           (ui-tile/ui-tiles))
      (->> tiles
           (group-by ::tile/title)
           (map (fn [[_ [tile :as tiles]]]
                  (comp/computed tile
                                 (merge {:stack-size (count tiles)}
                                        (if (and (= ::tile-type/god (::tile/type tile)) my-go? (::hand/my-go? hand))
                                          {:selectable? true
                                           :on-click    (fn [tile] (click-god-tile hand tile))}
                                          {})))))
           (sort-by (juxt ::tile/type ::tile/title))
           (ui-tile/ui-tiles)))))

(defsc Hand [this hand {:keys [epoch auction] :as computed}]
  {:query [::hand/available-sun-disks
           ::hand/used-sun-disks
           ::hand/my-go?
           ::hand/seat
           ::hand/score
           ::hand/id
           {::hand/tiles (comp/get-query ui-tile/Tile)}
           {::hand/player (comp/get-query ui-player/Player)}]
   :ident ::hand/id}
  (dom/div :.border-2.border-transparent.py-2.flex.flex-col
    (if (= (::hand/seat hand) (::hand/seat (::epoch/current-hand epoch)))
      {:classes ["bg-green-200"]}
      {:classes []})
    (dom/div :.flex.flex-row.justify-between {}
      (ui-player/ui-player (::hand/player hand))
      (dom/div :.px-4
        (if (and auction
                 (= (::hand/id (::epoch/last-ra-invoker epoch))
                    (::hand/id hand)))
          {}
          {:classes ["invisible"]})
        "Last called Auction")
      (dom/div {}
        (dom/span "score: ")
        (dom/span (str (::hand/score hand)))))
    (dom/div {}
      (ui-sun-disks hand computed))
    (dom/div {}
      (ui-tiles hand computed))))

(def ui-hand (comp/factory Hand {:keyfn ::hand/seat}))
