(ns ra.instrument
  (:require [datascript.core :as d]
            [ra.db :as db]
            [ra.model.game :as m-game]
            [ra.pathom :as pathom]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]
            [ra.model.tile :as m-tile]))

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

(defn god-tile? [t]
  (= ::tile-type/god (::tile/type t)))

(defn find-tile-in [thing pred]
  (first (filter pred thing)))

(defn find-tile-in-hand [hand pred]
  (first (filter pred (::hand/tiles hand))))

(defn find-tile-in-auction [epoch pred]
  (first (filter pred (::epoch/auction-tiles epoch))))

(defn draw-tile* [conn hand-id tile]
  (m-game/do-draw-tile conn
                       (d/entity @conn hand-id)
                       tile))

(defn draw-tile [conn hand-id tile-type]
  (draw-tile* conn hand-id (find-tile conn
                                      (:db/id (m-game/hand->game (d/entity @conn hand-id)))
                                      tile-type)))

(defn ra-pass-pass-pull [{:keys [::db/conn ::pathom/parser]} game-id h1 h2]
    (draw-tile* conn h1 (find-tile-by-type (get-game conn game-id) ::tile-type/ra))
    (parser {} [`(m-game/bid {::hand/id ~(::hand/id (d/entity @conn h2))
                              :sun-disk nil})])
    (parser {} [`(m-game/bid {::hand/id ~(::hand/id (d/entity @conn h1))
                              :sun-disk nil})])
    (draw-tile* conn h2 (find-tile-by-type (get-game conn game-id) ::tile-type/civilization)))

(defn god-tile-scenario [{:keys [::db/conn ::pathom/parser]} game-id]
  (let [game (d/entity @conn [::game/id game-id])
        epoch (::game/current-epoch game)
        h1 (:db/id (::epoch/current-hand epoch))
        h2 (:db/id (first (filter #(not= h1 (:db/id %))
                                  (::epoch/hands epoch))))]
    (draw-tile* conn h1 (find-tile-by-type (get-game conn game-id) ::tile-type/civilization))
    (draw-tile* conn h2 (find-tile-by-type (get-game conn game-id) ::tile-type/god))
    (parser {} [`(m-game/invoke-ra {::hand/id ~(::hand/id (d/entity @conn h1))})])
    (parser {} [`(m-game/bid {::hand/id ~(::hand/id (d/entity @conn h2))
                              :sun-disk ~(first (::hand/available-sun-disks (d/entity @conn h2)))})])
    (parser {} [`(m-game/bid {::hand/id ~(::hand/id (d/entity @conn h1))
                              :sun-disk nil})])
    (draw-tile* conn h2 (find-tile-by-type (get-game conn game-id) ::tile-type/civilization))
    (draw-tile* conn h1 (find-tile-by-type (get-game conn game-id) ::tile-type/god))
    nil))

(defn end-of-epoch-1-scenario [{:keys [::db/conn ::pathom/parser] :as env} game-id]
  (let [game (d/entity @conn [::game/id game-id])
        epoch (::game/current-epoch game)
        h1 (:db/id (::epoch/current-hand epoch))
        h2 (:db/id (first (filter #(not= h1 (:db/id %))
                                  (::epoch/hands epoch))))]
    (draw-tile* conn h1 (find-tile-by-type (get-game conn game-id) ::tile-type/civilization))
    (draw-tile* conn h2 (find-tile-by-type (get-game conn game-id) ::tile-type/god))
    (draw-tile* conn h1 (find-tile-p (get-game conn game-id) m-tile/pharoah?))
    (draw-tile* conn h2 (find-tile-p (get-game conn game-id) m-tile/monument?))

    ;; Invoke Ra and win some
    (parser {} [`(m-game/invoke-ra {::hand/id ~(::hand/id (d/entity @conn h1))})])
    (parser {} [`(m-game/bid {::hand/id ~(::hand/id (d/entity @conn h2))
                              :sun-disk ~(first (::hand/available-sun-disks (d/touch (d/entity @conn h2))))})])
    (parser {} [`(m-game/bid {::hand/id ~(::hand/id (d/entity @conn h1))
                              :sun-disk nil})])
    (draw-tile* conn h2 (find-tile-by-type (get-game conn game-id) ::tile-type/civilization))


    (ra-pass-pass-pull env game-id h1 h2)
    (ra-pass-pass-pull env game-id h1 h2)
    (ra-pass-pass-pull env game-id h1 h2)
    (ra-pass-pass-pull env game-id h1 h2)
    (ra-pass-pass-pull env game-id h1 h2)

    ;; Last hoora
    (draw-tile* conn h1 (find-tile-by-type (get-game conn game-id) ::tile-type/ra))

    nil))
