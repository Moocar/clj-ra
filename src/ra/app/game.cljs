(ns ra.app.game
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [ra.app.epoch :as ui-epoch]
            [ra.app.player :as ui-player]
            [ra.model.game :as m-game]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [com.fulcrologic.fulcro.data-fetch :as df]))

(defsc Game [this {:keys [::game/players
                          ::game/current-epoch
                          ::game/started-at
                          ::game/id
                          ui/current-player] :as props}]
  {:query [{::game/players (comp/get-query ui-player/Player)}
           {::game/current-epoch (comp/get-query ui-epoch/Epoch)}
           ;;           {::game/tile-bag (comp/get-query Tile)}
           ::game/started-at
           {[:ui/current-player '_] (comp/get-query ui-player/Player)}
           ::game/id]
   :ident ::game/id}
  (dom/div {}
    (if (first (filter #(= (::player/id %) (::player/id current-player))
                       players))
      (dom/div {}
        (if started-at
          (dom/div {}
            (dom/button {:primary true
                         :onClick (fn []
                                    (comp/transact! this [(m-game/reset {::game/id id})]))}
                        "Reset")
            (dom/button {:primary true
                        :onClick (fn []
                                   (df/load! this [::game/id id] Game))}
                       "Refresh")
            (ui-epoch/ui-epoch current-epoch))
          (dom/button {:primary true
                       :onClick (fn []
                                  (comp/transact! this [(m-game/start-game (select-keys props [::game/id]))]))}
                      "Start Game")))
      (dom/button {:primary true
                   :onClick (fn []
                              (comp/transact! this [(m-game/join-game {::game/id id ::player/id (::player/id current-player)})]))}
                  "Join Game"))))

(def ui-game (comp/factory Game {:keyfn ::game/id}))
