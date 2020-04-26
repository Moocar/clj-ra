(ns ra.app.game
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.semantic-ui.elements.button.ui-button :refer [ui-button]]
            [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
            [com.fulcrologic.semantic-ui.elements.segment.ui-segment :refer [ui-segment]]
            [com.fulcrologic.semantic-ui.elements.segment.ui-segment-group :refer [ui-segment-group]]
            [com.fulcrologic.semantic-ui.elements.label.ui-label :refer [ui-label]]
            [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]
            [com.fulcrologic.semantic-ui.collections.form.ui-form-field :refer [ui-form-field]]
            [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
            [com.fulcrologic.semantic-ui.views.card.ui-card :refer [ui-card]]
            [com.fulcrologic.semantic-ui.views.card.ui-card-group :refer [ui-card-group]]
            [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]
            [com.fulcrologic.semantic-ui.modules.modal.ui-modal-header :refer [ui-modal-header]]
            [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
            [ra.app.app :as client-app]
            [ra.specs.tile :as tile]
            [ra.specs.player :as player]
            [ra.specs.auction :as auction]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [ra.model.game :as m-game]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.model.player :as m-player]
            [clojure.string :as str]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [ra.specs.hand :as hand]
            [ra.specs.auction.bid :as bid]
            [ra.specs.auction.reason :as auction-reason]))

(defn ui-clickable-sun-disk [{:keys [onClick value]}]
  (ui-button {:circular true
              :color    "brown"
              :key      value
              :onClick  (fn [_] (onClick))}
            (str value)))

(defn ui-sun-disk [{:keys [value used?]}]
  (ui-label {:circular true
             :color    (if used? "gray" "brown")
             :key      value}
            (str value)))

(defsc Tile [_ {:keys [::tile/title]}]
  {:query [::tile/id
           ::tile/title
           ::tile/disaster?
           ::tile/type]}
  (ui-card {:style {:height "50"
                    :width "50"}}
           title))

(def ui-tile (comp/factory Tile {:keyfn ::tile/id}))

(defsc Player [_ {:keys [::player/name]}]
  {:query [::player/name
           ::player/id]}
  (dom/strong name))

(def ui-player (comp/factory Player {:keyfn ::player/id}))

(defn all-passes? [{:keys [::auction/bids]}]
  (empty? (filter ::bid/sun-disk bids)))

(defn can-pass? [{:keys [::auction/ra-hand ::auction/reason ::auction/tiles-full?] :as auction} hand]
  (or (not= (::hand/id ra-hand) (::hand/id hand))
      (not= reason ::auction-reason/invoke)
      (not (all-passes? auction))
      tiles-full?))

(defn auction-tiles-full? [epoch]
  (= 8 (count (::epoch/auction-tiles epoch))))

(defsc Hand [this
             {:keys [::hand/available-sun-disks
                     ::hand/used-sun-disks
                     ::hand/tiles
                     ::hand/seat
                     ::hand/id
                     ::hand/player
                     ::hand/my-go?] :as hand}
             {:keys [onClickSunDisk highest-bid auction epoch]}]
  {:query [::hand/available-sun-disks
           ::hand/used-sun-disks
           ::hand/my-go?
           ::hand/seat
           ::hand/id
           {::hand/tiles (comp/get-query Tile)}
           {::hand/player (comp/get-query Player)}]
   :ident ::hand/id}
  (ui-segment {}
              (dom/span (ui-player player) " - "
                        (str "seat: " seat))
              (ui-segment {:compact true}
                          (dom/div {}
                            (concat
                             (map (fn [sun-disk]
                                    (if (and my-go? onClickSunDisk (> sun-disk highest-bid) )
                                      (ui-clickable-sun-disk {:onClick #(onClickSunDisk sun-disk)
                                                              :value   sun-disk})
                                      (ui-sun-disk {:value sun-disk})))
                                  available-sun-disks)
                             (map (fn [sun-disk]
                                    (ui-sun-disk {:value sun-disk :used? true}))
                                  used-sun-disks)
                             (when (and onClickSunDisk my-go? (can-pass? auction hand))
                               [(ui-clickable-sun-disk {:onClick #(onClickSunDisk nil)
                                                         :value   "Pass"})]))))
    (ui-segment {:compact true}
                (ui-card-group {} (map ui-tile tiles)))
    (when (and my-go? (not auction))
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
                   "Invoke Ra")))))

(def ui-hand (comp/factory Hand {:keyfn ::hand/seat}))

(defn highest-bid [{:keys [::auction/bids]}]
  (apply max (or (seq (map ::bid/sun-disk bids)) [0])))

(defsc Auction [this {:keys [::auction/reason ::auction/bids]}]
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
                         (ui-sun-disk {:value sun-disk})
                         (get-in hand [::hand/player ::player/name])))
                     bids))))

(def ui-auction (comp/factory Auction))

(defsc Epoch [this {:keys [::epoch/number
                           ::epoch/auction-tiles
                           ::epoch/ra-tiles
                           ::epoch/current-sun-disk
                           ::epoch/auction
                           ::epoch/hands
                           ::epoch/current-hand]
                    :as props}]
  {:query [::epoch/current-sun-disk
           ::epoch/number
           ::epoch/id
           {::epoch/auction (comp/get-query Auction)}
           {::epoch/current-hand [::hand/seat]}
           {::epoch/ra-tiles (comp/get-query Tile)}
           {::epoch/auction-tiles (comp/get-query Tile)}
           {::epoch/last-ra-invokee (comp/get-query Player)}
           {::epoch/hands (comp/get-query Hand)}]
   :ident ::epoch/id}
  (js/console.log props)
  (dom/div {}
    (dom/p (str "Epoch: " number))
    (ui-segment {:compact true}
                (dom/strong "Ra track")
                (ui-card-group {} (map ui-tile ra-tiles)))
    (ui-segment {:compact "true"}
      (ui-sun-disk {:value current-sun-disk}))
    (ui-segment {:compact true}
                (dom/strong "Auction track")
                (ui-card-group {} (map ui-tile auction-tiles)))
    (when auction
      (ui-auction auction))
    (ui-segment {:compact true}
                (dom/h3 "Seats")
                (ui-segment-group {:horizontal true}
                                  (map (fn [{:keys [::hand/seat] :as hand}]
                                         (ui-hand
                                          (if auction
                                            (comp/computed hand {:onClickSunDisk (fn [sun-disk]
                                                                                   (js/console.log "sun disk clicked" sun-disk)
                                                                                   (comp/transact! this [(m-game/bid {::hand/id (::hand/id hand) :sun-disk sun-disk})]))
                                                                 :highest-bid    (highest-bid auction)
                                                                 :auction        auction})
                                            (comp/computed hand {:epoch props})))

                        #_(if (= seat (::hand/seat current-hand))
                            (ui-segment dom/div {:style {:backgroundColor "pink"}}
                            (ui-hand hand))
                          (ui-hand hand)))
                      hands)))))

(def ui-epoch (comp/factory Epoch {:keyfn ::epoch/number}))

(defsc Game [this {:keys [::game/players
                          ::game/current-epoch
                          ::game/started-at
                          ::game/id
                          ui/current-player] :as props}]
  {:query [{::game/players (comp/get-query Player)}
           {::game/current-epoch (comp/get-query Epoch)}
           ;;           {::game/tile-bag (comp/get-query Tile)}
           ::game/started-at
           {[:ui/current-player '_] (comp/get-query Player)}
           ::game/id]
   :ident ::game/id}
  (dom/div {}
    (if (first (filter #(= (::player/id %) (::player/id current-player))
                       players))
      (dom/div {}
        (if started-at
          (dom/div {}
            (ui-button {:primary true
                        :onClick (fn []
                                   (comp/transact! this [(m-game/reset {::game/id id})]))}
                       "Reset")
            (ui-epoch current-epoch))
          (ui-button {:primary true
                      :onClick (fn []
                                 (comp/transact! this [(m-game/start-game (select-keys props [::game/id]))]))}
                     "Start Game")))
      (ui-button {:primary true
                  :onClick (fn []
                             (comp/transact! this [(m-game/join-game {::game/id id ::player/id (::player/id current-player)})]))}
                 "Join Game"))))

(def ui-game (comp/factory Game {:keyfn ::game/id}))
