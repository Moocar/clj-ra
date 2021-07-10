(ns ra.instrument
  (:require [datascript.core :as d]
            [ra.bot :as bot]
            [ra.db :as db]
            [ra.model.game :as m-game]
            [ra.model.player :as m-player]
            [ra.model.tile :as m-tile]
            [ra.pathom :as pathom]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]))

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
  (let [game (get-game @conn game-short-id)
        players (::game/players game)]
    (doseq [player players]
      (d/unlisten! conn (::player/id player)))
    (assert game)
    (parser {} [`(m-game/reset {::game/id ~(::game/id game)})])
    (m-game/notify-all-clients! env (::game/id game))
    nil))

(defn clear-bots! [{:keys [::db/conn ::pathom/parser] :as env}]
  (reset! (:listeners (meta conn)) {}))

(defn rand-char []
  (char (+ 65 (rand-int 26))))

(defn new-bot-name []
  (apply str (concat ["Bot "] (repeatedly 4 rand-char))))

(defn add-bot [{:keys [::db/conn ::pathom/parser] :as env} game-short-id]
  (let [websockets  (:websockets (:ra.server/websockets env))
        parser-env  {:connected-uids (:connected-uids websockets)
                     :websockets     websockets}
        player-id   (db/uuid)
        player-name (new-bot-name)
        game        (get-game @conn game-short-id)]
    (assert game)
    (parser {} [`(m-player/new-player {::player/id ~player-id})])
    (parser {} [`(m-player/save {::player/id   ~player-id
                                 ::player/name ~player-name})])
    (d/listen! conn
               player-id
               (fn [tx-report]
                 (when (= (::game/id game) (::game/id (:tx-meta tx-report)))
                   (bot/handle-change (assoc env :parser-env parser-env)
                                      player-id
                                      (::game/id game)))))
    (parser parser-env [`(m-game/join-game {::player/id ~player-id
                                            ::game/id   ~(::game/id game)})])
    nil))
