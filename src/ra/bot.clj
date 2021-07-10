(ns ra.bot
  (:require [datascript.core :as d]
            [ra.specs.player :as player]
            [ra.db :as db]
            [ra.pathom :as pathom]
            [ra.specs.game :as game]
            [ra.specs.epoch :as epoch]
            [ra.specs.hand :as hand]
            [ra.model.game :as m-game]
            [ra.specs.tile :as tile]
            [clojure.set :as set]))

(defn handle-change [{:keys [::db/conn ::pathom/parser parser-env] :as env} player-id game-id]
  (assert player-id)
  (assert game-id)
  (let [player (d/entity @conn [::player/id player-id])
        game   (d/entity @conn [::game/id game-id])]
    (assert player)
    (assert game)
    (if-not (::game/started-at game)
      nil
      (let [epoch        (::game/current-epoch game)
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
                (let [tiles                  (set (::hand/tiles current-hand))
                      disaster-tiles         (set (filter ::tile/disaster? tiles))
                      without-disaster-tiles (set/difference tiles disaster-tiles)
                      selected-tiles         (loop [acc        []
                                                    tile-types (mapcat (fn [t] (repeat 2 (::tile/type t))) disaster-tiles)
                                                    tiles      without-disaster-tiles]
                                       (if (or (empty? tile-types) (empty? tiles))
                                         acc
                                         (let [found (first (filter (fn [tile]
                                                                      (= (::tile/type tile) (first tile-types)))
                                                                    tiles))]
                                           (if found
                                             (recur (conj acc found)
                                                    (rest tile-types)
                                                    (disj tiles found))
                                             (recur acc
                                                    (rest tile-types)
                                                    tiles)))))]
                  (parser parser-env [`(m-game/discard-disaster-tiles {::hand/id ~(::hand/id current-hand)
                                                                       :tile-ids ~(map ::tile/id selected-tiles)})]))
                (if (m-game/auction-tiles-full? epoch)
                  (parser parser-env [`(m-game/invoke-ra {::hand/id ~(::hand/id current-hand)})])
                  (parser parser-env [`(m-game/draw-tile {::hand/id ~(::hand/id current-hand)})]))))))))))
