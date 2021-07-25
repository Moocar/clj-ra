(ns ra.app.event
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [ra.specs.game :as game]
            [ra.specs.game.event :as event]
            [ra.specs.game.event.type :as event-type]
            [ra.specs.player :as player]
            [com.fulcrologic.fulcro.application :as app]
            [ra.specs.hand :as hand]
            [ra.specs.tile :as tile]
            [ra.specs.auction.bid :as bid]
            [ra.app.sun-disk :as ui-sun-disk]
            [ra.app.tile :as ui-tile]))

;; Events
;; Approaches

;; 1. text only
;; Can't highlight player's name
;; Can't show tiles for tiles played

;; 1. Generic data. Actually, that might work

(defmulti ui-body
  (fn [_ event-type _]
    event-type))

(defn ui-player-name [thing]
  (dom/div :.font-bold {} (::player/name thing)))

(defn ui-tile [tile]
  (dom/div :.rounded.flex.justify-center.px-2
    {:classes (get ui-tile/type-classes (::tile/type tile))}
    (::tile/title tile)))

(defmethod ui-body ::event-type/join-game [s _ {:keys [player]}]
  (let [player (get-in s [::player/id (::player/id player)])]
    (dom/div :.flex.flex-row.gap-2 {}
      (ui-player-name player)
      " joined game")))

(defmethod ui-body ::event-type/leave-game [s _ {:keys [player]}]
  (let [player (get-in s [::hand/id (::player/id player)])]
    (dom/div :.flex.flex-row.gap-2 {}
      (ui-player-name player)
      " left game")))

(defmethod ui-body ::event-type/game-started [_ _ _]
  (dom/div :.flex.flex-row.border-t-2.mt-2 {}
    "Game started"))

(defmethod ui-body ::event-type/draw-tile [s _ {:keys [hand tile]}]
  (dom/div :.flex.flex-row.gap-2 {}
    (let [hand (get-in s [::hand/id (::hand/id hand)])
          player (get-in s (::hand/player hand))]
      (ui-player-name player))
    (dom/div {} " drew a ")
    (ui-tile tile)))

(defn ui-sun-disk [value]
  (dom/div :.rounded.bg-red-300.flex.justify-center.px-2 {}
    (dom/div {} (str value))))

(defmethod ui-body ::event-type/bid [s _ {:keys [hand sun-disk last? winning-bid tiles-won]}]
  (dom/div {} (if last?
                (dom/div :.flex.flex-col.border-t-2 {}
                  (if winning-bid
                    (let [hand   (get-in s [::hand/id (::hand/id (::bid/hand winning-bid))])
                          player (get-in s (::hand/player hand))]
                      (dom/div :.flex.flex-row.gap-2 {}
                        (ui-player-name player)
                        " won auction with "
                        (ui-sun-disk (::bid/sun-disk winning-bid))
                        (dom/ul :.pl-4 (map (fn [t] (dom/li (ui-tile t))) tiles-won))))
                    "Auction finished (everyone passed)"))
                (let [hand   (get-in s [::hand/id (::hand/id hand)])
                      player (get-in s (::hand/player hand))]
                  (dom/div :.flex.flex-row.gap-2 {}
                    (ui-player-name player)
                    " bid "
                    (dom/div :.font-bold {} (ui-sun-disk (or sun-disk "Pass"))))))))

(defmethod ui-body ::event-type/finish-epoch [_ _ _]
  (dom/div :.flex.flex-col {}
    (dom/div :.flex.flex-row.gap-2.border-t-2 {}
      "Epoch Finished")))

(defmethod ui-body ::event-type/invoke-ra [s _ {:keys [hand]}]
  (dom/div :.flex.flex-row.gap-2 {}
    (let [hand (get-in s [::hand/id (::hand/id hand)])
          player (get-in s (::hand/player hand))]
      (ui-player-name player))
    (dom/div :.rounded.bg-red-500.flex.justify-center.px-2.text-white {} " invoked Auction")))

(defmethod ui-body ::event-type/discard-disaster-tiles [s _ {:keys [hand]}]
  (dom/div :.flex.flex-row.gap-2 {}
    (let [hand (get-in s [::hand/id (::hand/id hand)])
          player (get-in s (::hand/player hand))]
      (ui-player-name player))
    (dom/div {} " discarded disaster tiles")))

(defmethod ui-body ::event-type/use-god-tile [s _ {:keys [hand tile]}]
  (dom/div :.flex.flex-row.gap-2 {}
    (let [hand (get-in s [::hand/id (::hand/id hand)])
          player (get-in s (::hand/player hand))]
      (ui-player-name player))
    (dom/div {} " used god tile: ")
    (ui-tile tile)))

(defmethod ui-body :default [_ event-type _]
  (dom/div :.flex.flex-row {}
    (str event-type)))

(defsc Item [this props]
  {:query [::event/id
           ::event/type
           ::event/data]
   :ident ::event/id}
  (dom/div {}
    (ui-body (app/current-state this) (::event/type props) (::event/data props))))

(def ui-item (comp/factory Item {:keyfn ::event/id}))

(defn ui-items [events]
  (dom/div :.flex.flex-col.overflow-y-scroll.h-48.w-max.gap-2 {}
           (map ui-item events)))
