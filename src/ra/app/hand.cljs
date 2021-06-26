(ns ra.app.hand
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.semantic-ui.elements.button.ui-button
             :refer
             [ui-button]]
            [com.fulcrologic.semantic-ui.elements.segment.ui-segment
             :refer
             [ui-segment]]
            [ra.app.player :as ui-player]
            [ra.app.tile :as ui-tile]
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

(defsc Hand [this
             {:keys [::hand/available-sun-disks
                     ::hand/used-sun-disks
                     ::hand/tiles
                     ::hand/seat
                     ::hand/id
                     ::hand/player
                     ::hand/my-go?] :as hand}
             {:keys [onClickSunDisk highest-bid auction epoch click-god-tile]}]
  {:query [::hand/available-sun-disks
           ::hand/used-sun-disks
           ::hand/my-go?
           ::hand/seat
           ::hand/score
           ::hand/id
           {::hand/tiles (comp/get-query ui-tile/Tile)}
           {::hand/player (comp/get-query ui-player/Player)}]
   :ident ::hand/id}
  (let [discard-disaster-tiles? (seq (filter ::tile/disaster? tiles))
        my-go?                  (and my-go?  (not discard-disaster-tiles?))]
    (ui-segment (cond-> {}
                  (= seat (::hand/seat (::epoch/current-hand epoch)))
                  (assoc-in [:style :backgroundColor] "pink"))
      (dom/span (ui-player/ui-player player) " - "
                (str "seat: " seat) " - "
                (str "score: " (::hand/score hand)))
      (ui-segment {:compact true}
        (dom/div {}
          (concat
           (map (fn [sun-disk]
                  (if (and my-go? onClickSunDisk (> sun-disk highest-bid) )
                    (ui-tile/ui-clickable-sun-disk {:onClick #(onClickSunDisk sun-disk)
                                                    :value   sun-disk})
                    (ui-tile/ui-sun-disk {:value sun-disk})))
                available-sun-disks)
           (map (fn [sun-disk]
                  (ui-tile/ui-sun-disk {:value sun-disk :used? true}))
                used-sun-disks)
           (when (and onClickSunDisk my-go? (can-pass? auction hand))
             [(ui-tile/ui-clickable-sun-disk {:onClick #(onClickSunDisk nil)
                                              :value   "Pass"})]))))
      (let [disaster-types (set (map ::tile/type (filter ::tile/disaster? tiles)))]
        (ui-segment {:compact true}
          (ui-tile/ui-tiles (map (fn [tile]
                                   (comp/computed tile
                                                  (if discard-disaster-tiles?
                                                    (if (and (not (::tile/disaster? tile))
                                                             (disaster-types (::tile/type tile)))
                                                      {:selectable? true}
                                                      {:dimmed? true})
                                                    (if (= ::tile-type/god (::tile/type tile))
                                                      {:selectable? true
                                                       :on-click click-god-tile}
                                                      tiles))))
                                 tiles))))
      (if discard-disaster-tiles?
        (dom/div {}
          (ui-button {:style   {:marginTop "10"}
                      :primary true
                      :onClick (fn []
                                 (comp/transact! this [(m-game/discard-disaster-tiles
                                                        {::hand/id id
                                                         :tile-ids (map ::tile/id (filter :ui/selected? (::hand/tiles hand)))})]))}
                     "Discard disaster tiles"))
        (when (and my-go? (not auction) (not (::epoch/in-disaster? epoch)))
          (dom/div {}
           (when-not (auction-tiles-full? epoch)
             (ui-button {:style   {:marginTop "10"}
                         :primary true
                         :onClick (fn []
                                    (comp/transact! this [(m-game/draw-tile {::hand/id id})]))}
                        "Draw Tile"))
           (ui-button {:style   {:marginTop "10"}
                       :primary true
                       :onClick (fn []
                                  (comp/transact! this [(m-game/invoke-ra {::hand/id id})]))}
                      "Invoke Ra")))))))

(def ui-hand (comp/factory Hand {:keyfn ::hand/seat}))
