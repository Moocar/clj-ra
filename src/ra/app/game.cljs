(ns ra.app.game
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [ra.app.epoch :as ui-epoch]
            [ra.app.player :as ui-player]
            [ra.model.game :as m-game]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [ra.app.ui :as ui]
            [ra.specs.epoch :as epoch]))

(declare Game)

(defn ui-game-info [this props]
  (let [epoch (::game/current-epoch props)]
    (dom/div {}
      (dom/p {} (str "Epoch: " (::epoch/number epoch)))
      (dom/div :.flex.flex-col.space-y-4 {}
               (ui/button {:onClick (fn []
                                      (comp/transact! this [(m-game/reset {::game/id (::game/id props)})]))}
                 "Reset")
               (ui/button {:onClick (fn []
                                      (df/load! this [::game/id (::game/id props)] Game))}
                 "Refresh")))))

(defsc Game [this {:keys                                         [::game/players
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
    (dom/div {}
      (dom/span :.text-black-700.text-md.font-bold.mb-2 {} "Game")
      (dom/span :.text-black-300.text-md.mb-2.pl-4 {} (str "(" (str (::game/id props)) ")")))
    (if (first (filter #(= (::player/id %) (::player/id current-player))
                       players))
      ;; Game exists and you're in it
      (dom/div {}
        (if started-at
          ;; Game started
          (dom/div {}
            (dom/div :.float-right {}
                     (ui-game-info this props))
            (ui-epoch/ui-epoch current-epoch))
          ;; Game exists but not started
          (dom/div {}
            (dom/h3 :.text-black-700.text-md.font-bold.mb-2 {} "Players")
            (dom/ul {}
                    (map (fn [player]
                           (dom/li {} (::player/name player)))
                         (::game/players props)))
            (ui/button {::onClick (fn []
                                    (comp/transact! this [(m-game/start-game (select-keys props [::game/id]))]))}
              "Start Game"))))
      ;; Game exists but you're not in it
      (ui/button {:onClick (fn []
                             (comp/transact! this [(m-game/join-game {::game/id id ::player/id (::player/id current-player)})]))}
        "Join Game"))))

(def ui-game (comp/factory Game {:keyfn ::game/id}))
