(ns ra.instrument
  (:require [datascript.core :as d]
            [ra.db :as db]
            [ra.model.game :as m-game]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.tile.type :as tile-type]
            [ra.specs.tile :as tile]))

(defn get-game [conn game-id]
  (d/entity @conn [::game/id game-id]))

(defn find-tile-p [game pred]
  (first (filter pred (::game/tile-bag game))))

(defn find-tile-by-type [game tile-type]
  (find-tile-p game (fn [tile]
                      (::tile/type tile)
                      (and (= tile-type (::tile/type tile))
                           (not (::tile/disaster? tile))))))

(defn find-tile [conn game-id tile-type]
  (find-tile-p (d/entity @conn game-id) (fn [tile]
                              (and (= tile-type (::tile/type tile))
                                   (not (::tile/disaster? tile))))))

(defn draw-tile* [conn hand-id tile]
  (m-game/do-draw-tile conn
                       (d/entity @conn hand-id)
                       tile))

(defn draw-tile [conn hand-id tile-type]
  (draw-tile* conn hand-id (find-tile conn
                                      (:db/id (m-game/hand->game (d/entity @conn hand-id)))
                                      tile-type)))

(defn progress-game [{:keys [::db/conn]} game-id]
  (let [game (d/entity @conn [::game/id game-id])
        epoch (::game/current-epoch game)
        h1 (:db/id (::epoch/current-hand epoch))
        h2 (:db/id (first (filter #(not= h1 (:db/id %))
                                  (::epoch/hands epoch))))]
    (draw-tile* conn h1 (find-tile-by-type (get-game conn game-id) ::tile-type/civilization))
    (draw-tile* conn h2 (find-tile-by-type (get-game conn game-id) ::tile-type/god))
    (draw-tile* conn h1 (find-tile-by-type (get-game conn game-id) ::tile-type/god))
    nil))
