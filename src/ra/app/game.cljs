(ns ra.app.game
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.semantic-ui.elements.button.ui-button :refer [ui-button]]
            [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
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
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [ra.model.game :as m-game]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.model.player :as m-player]
            [clojure.string :as str]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [ra.specs.hand :as hand]))

(defsc Tile [_ {:keys [::tile/title] :as tile}]
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
  (dom/p (dom/strong name)))

(def ui-player (comp/factory Player {:keyfn ::player/id}))

(defsc Hand [this {:keys [::hand/available-sun-disks
                          ::hand/tiles
                          ::hand/seat
                          ::hand/id
                          ::hand/player
                          ::hand/my-go?]}]
  {:query [::hand/available-sun-disks
           ::hand/my-go?
           ::hand/seat
           ::hand/id
           {::hand/tiles (comp/get-query Tile)}
           {::hand/player (comp/get-query Player)}]
   :ident ::hand/id}
  (dom/div {}
    (ui-player player)
    (dom/p "seat: " seat)
    (dom/p (str "Available sun disks: " (str/join ", " available-sun-disks)))
    (ui-card-group {} (map ui-tile tiles))
    (when my-go?
      (ui-button {:style {:marginTop "10"}
                  :primary true
                  :onClick (fn []
                             (comp/transact! this [(m-game/draw-tile {::hand/id id})]))}
                 "Draw Tile"))))

(def ui-hand (comp/factory Hand {:keyfn ::hand/seat}))

(defsc Epoch [_ {:keys [::epoch/number
                        ::epoch/auction-tiles
                        ::epoch/ra-tiles
                        ::epoch/current-sun-disk
                        ::epoch/hands
                        ::epoch/current-hand]}]
  {:query [::epoch/current-sun-disk
           ::epoch/number
           ::epoch/id
           {::epoch/current-hand [::hand/seat]}
           {::epoch/ra-tiles (comp/get-query Tile)}
           {::epoch/auction-tiles (comp/get-query Tile)}
           {::epoch/last-ra-invokee (comp/get-query Player)}
           {::epoch/hands (comp/get-query Hand)}]
   :ident ::epoch/id}
  (dom/div {}
    (dom/p (str "Epoch: " number))
    (dom/p (str "Middle Sun Disk: " current-sun-disk))
    (ui-card-group {} (map ui-tile ra-tiles))
    (ui-card-group {} (map ui-tile auction-tiles))
    (dom/ul
     (map (fn [{:keys [::hand/seat] :as hand}]
            (if (= seat (::hand/seat current-hand))
              (dom/div {:style {:backgroundColor "pink"}}
                (ui-hand hand))
              (ui-hand hand)))
          hands))))

(def ui-epoch (comp/factory Epoch {:keyfn ::epoch/number}))

(defsc Game [this {:keys [::game/players
                          ::game/current-epoch
                          ::game/started-at
                          ::game/id
                          current-player] :as props}]
  {:query [{::game/players (comp/get-query Player)}
           {::game/current-epoch (comp/get-query Epoch)}
           ;;           {::game/tile-bag (comp/get-query Tile)}
           ::game/started-at
           {[:current-player '_] (comp/get-query Player)}
           ::game/id]
   :ident ::game/id}
  (dom/div {}
    (if (first (filter #(= (::player/id %) (::player/id current-player))
                       players))
      (dom/div {}
        (if started-at
          (dom/div {}
            (dom/p "game started")
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
