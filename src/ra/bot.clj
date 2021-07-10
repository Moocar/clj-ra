(ns ra.bot
  (:require [datascript.core :as d]
            [ra.specs.player :as player]
            [ra.db :as db]
            [ra.pathom :as pathom]
            [ra.specs.game :as game]
            [ra.specs.epoch :as epoch]
            [ra.specs.hand :as hand]
            [ra.model.game :as m-game]))

(defn handle-change [{:keys [::db/conn ::pathom/parser parser-env] :as env} player-id game-id]
  (assert player-id)
  (assert game-id)
  (let [player (d/entity @conn [::player/id player-id])
        game (d/entity @conn [::game/id game-id])]
    (assert player)
    (assert game)
    (if-not (::game/started-at game)
      nil
      (let [epoch (::game/current-epoch game)
            current-hand (::epoch/current-hand epoch)]
        (if-not (= player (::hand/player current-hand))
          nil
          (future
            (Thread/sleep 1000)
            (if (::epoch/auction epoch)
              (let [available-sun-disks (::hand/available-sun-disks current-hand)]
                (parser parser-env [`(m-game/bid {::hand/id ~(::hand/id current-hand)
                                                  :sun-disk ~(rand-nth (vec available-sun-disks))})]))
              (if (::epoch/in-disaster? epoch)
                (str "Bot turn: " (::player/name player) " discard disaster")
                (if (m-game/auction-tiles-full? epoch)
                  (parser parser-env [`(m-game/invoke-ra {::hand/id ~(::hand/id current-hand)})])
                  (parser parser-env [`(m-game/draw-tile {::hand/id ~(::hand/id current-hand)})]))))))))))
