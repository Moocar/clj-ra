(ns ra.instrument
  (:require [datascript.core :as d]
            [ra.db :as db]
            [ra.model.game :as m-game]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.tile.type :as tile-type]
            [ra.specs.tile :as tile]))

(defn find-tile [conn game-id tile-type]
  (first (filter (fn [tile]
                   (and (= tile-type (::tile/type tile))
                        (not (::tile/disaster? tile))))
                 (::game/tile-bag (d/entity @conn game-id)))))

(defn draw-tile [conn hand-id tile-type]
  (m-game/do-draw-tile conn
                       (d/entity @conn hand-id)
                       (find-tile conn
                                  (:db/id (m-game/hand->game (d/entity @conn hand-id)))
                                  tile-type)))

(defn progress-game [{:keys [::db/conn]} game-id]
  (let [game (d/entity @conn [::game/id game-id])
        epoch (::game/current-epoch game)
        h1 (:db/id (::epoch/current-hand epoch))
        h2 (:db/id (first (filter #(not= h1 (:db/id %))
                                  (::epoch/hands epoch))))]
    (draw-tile conn h1 ::tile-type/civilization)
    (draw-tile conn h2 ::tile-type/civilization)
    (draw-tile conn h1 ::tile-type/ra)
    nil))
