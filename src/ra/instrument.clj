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

(defn get-hands [epoch hand-count]
  (->> (::epoch/hands epoch)
       (sort-by ::hand/seat)
       (repeat 2)
       (apply concat)
       (drop-while #(not= (:db/id (::epoch/current-hand epoch)) (:db/id %)))
       (take hand-count)))

(defn get-game [db short-id]
  (d/entity db [::game/short-id short-id]))

(defn get-hand [db hand-id]
  (d/entity db [::hand/id hand-id]))

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

(defn draw-tile* [conn hand tile]
  (let [db @conn
        hand (d/entity db (:db/id hand))
        tx (if (m-tile/ra? tile)
             (m-game/draw-ra-tx hand tile)
             (m-game/draw-normal-tile-tx hand tile))]
    (d/transact! conn (concat tx (m-game/event-tx (m-game/hand->game hand)
                                                  (str (m-game/hand->player-name hand) " Drew tile " (::tile/title tile)) )))))

(defn my-scenario [{:keys [::db/conn ::pathom/parser] :as env} game-short-id]
  (let [game (get-game @conn game-short-id)
        epoch (::game/current-epoch game)
        [h1 h2 h3] (get-hands epoch 3)]
    (draw-tile* conn h1 (find-tile-p game m-tile/god?))
    (draw-tile* conn h2 (find-tile-p game m-tile/monument?))
    (draw-tile* conn h3 (find-tile-p game ::tile/disaster?))
    (m-game/notify-all-clients! env (::game/id game))
    nil))

(defn reset [{:keys [::db/conn ::pathom/parser] :as env} game-short-id]
  (let [game (get-game @conn game-short-id)]
    (parser {} [`(m-game/reset {::game/-id ~(::game/id game)})])
    (m-game/notify-all-clients! env (::game/id game))
    nil))
