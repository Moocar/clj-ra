(ns ra.app.client
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.semantic-ui.elements.button.ui-button :refer [ui-button]]
            [ra.app.app :as client-app]
            [ra.specs.tile :as tile]
            [ra.specs.sun-disk :as sun-disk]
            [ra.specs.player :as player]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]))

(defsc Tile [_ _]
  {:query [::tile/id
           ::tile/title
           ::tile/disaster?
           ::tile/type]})

(defsc SunDisk [_ _]
  {:query [::sun-disk/number
           ::sun-disk/used?]})

(defsc PlayerHand [_ _]
  {:query [{::player/tiles (comp/get-query Tile)}
           {::player/sun-disks (comp/get-query SunDisk)}
           ::player/id]})

(defsc Player [_ _]
  {:query [::player/name
           ::player/id
           ::player/score]})

(defsc Epoch [_ _]
  {:query [{::epoch/current-sun-disk (comp/get-query SunDisk)}
           ::epoch/number
           {::epoch/ra-tiles (comp/get-query Tile)}
           {::epoch/auction-tiles (comp/get-query Tile)}
           {::epoch/last-ra-invokee (comp/get-query Player)}
           {::epoch/player-hands (comp/get-query PlayerHand)}]})

(defsc Game [_ _]
  {:query [{::game/players (comp/get-query Player)}
           {::game/current-epoch (comp/get-query Epoch)}
;;           {::game/tile-bag (comp/get-query Tile)}
           ::game/id]
   :ident (fn [] [:component/id :ra.app.game])})

(defsc Root [this {::app/keys [active-remotes]}]
  {:query [[::app/active-remotes '_]]
   :initial-state {}}
  (dom/div {}
    (when (seq active-remotes)
      (dom/div :.ui.active.inline.loader))
    (ui-button {:primary true
                :onClick (fn []
                           #_(comp/transact! this [(m-game/new-game {})]))}
               "New Game")
    (dom/p "hi there")))

(defn ^:export refresh []
  (app/mount! client-app/APP Root "app"))

(defn ^:export start []
  (app/mount! client-app/APP Root "app")
  (dr/initialize! client-app/APP))
