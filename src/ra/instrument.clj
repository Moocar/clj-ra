(ns ra.instrument
  (:require [datascript.core :as d]
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
            [ra.specs.tile.type :as tile-type]
            [ra.model.bot :as m-bot]))

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

(defn refresh-game [db game]
  (let [game (get-game db (::game/short-id game))
        epoch (::game/current-epoch game)
        hands (get-hands epoch 3)]
    {:game game
     :epoch epoch
     :hands hands}))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actions

(defn draw-tile* [{:keys [::db/conn ::pathom/parser] :as env} hand tile]
  (let [db @conn
        hand (d/entity db (:db/id hand))
        game (m-game/hand->game hand)
        epoch (m-game/hand->epoch hand)]
    (m-game/do-draw-tile env game epoch hand tile)))

(defn rand-bid [{:keys [::pathom/parser]} hand game]
  (parser {} [`(m-game/bid {::hand/id ~(::hand/id hand)
                            ::game/id ~(::game/id game)
                            :sun-disk ~(first (::hand/available-sun-disks hand))})]))

(defn pass-bid [{:keys [::pathom/parser]} hand game]
  (parser {} [`(m-game/bid {::hand/id ~(::hand/id hand)
                            ::game/id ~(::game/id game)
                            :sun-disk nil})]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scenarios

(defn draws-bid-pass-pass
  [{:keys [::db/conn ::pathom/parser] :as env}
   game
   & {:keys [winner hands]}]
  (let [[h1 h2 h3] hands]
    (draw-tile* env h1 (find-tile-p game tile/civ?))
    (draw-tile* env h2 (find-tile-p game tile/pharoah?))
    (draw-tile* env h3 (find-tile-p game tile/ra?))
    (if (= winner h1)
      (rand-bid env h1 game)
      (pass-bid env h1 game))
    (if (= winner h2)
      (rand-bid env h2 game)
      (pass-bid env h2 game))
    (if (= winner h3)
      (rand-bid env h3 game)
      (pass-bid env h3 game))
    (refresh-game @conn game)))

(comment
  ;; examples
  (let [game                 (get-game @conn game-short-id)
        epoch                (::game/current-epoch game)
        [h1 h2 h3 :as hands] (get-hands epoch 3)
        {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        _                    (draw-tile* env (first hands) (find-tile-p game tile/monument?))
        {:keys [game hands]} (refresh-game @conn game)
        ]
    (m-game/notify-all-clients! env (::game/id game))
    nil))

;; Get to the end of the first epoch quickly
(defn run [{:keys [::db/conn ::pathom/parser] :as env} game-short-id]
  (let [game (get-game @conn game-short-id)
        epoch (::game/current-epoch game)
        [h1 h2 h3 :as hands] (get-hands epoch 3)
        {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        _ (draw-tile* env (first hands) (find-tile-p game tile/monument?))
        {:keys [game hands]} (refresh-game @conn game)
        {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        _ (draw-tile* env (first hands) (find-tile-p game tile/monument?))
        {:keys [game hands]} (refresh-game @conn game)
        {:keys [game hands]} (draws-bid-pass-pass env game :winner h3 :hands hands)
;        _ (draw-tile* env (first hands) (find-tile-p game m-tile/ra?))

        ;; game (get-game @conn game-short-id)
        ;; epoch (::game/current-epoch game)
        ;; [h1 h2 h3 :as hands] (get-hands epoch 3)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        ;; _ (draw-tile* env (first hands) (find-tile-p game m-tile/monument?))
        ;; {:keys [game hands]} (refresh-game @conn game)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        ;; _ (draw-tile* env (first hands) (find-tile-p game m-tile/monument?))
        ;; {:keys [game hands]} (refresh-game @conn game)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h3 :hands hands)
        ;; _ (draw-tile* env (first hands) (find-tile-p game m-tile/ra?))

        ;; game (get-game @conn game-short-id)
        ;; epoch (::game/current-epoch game)
        ;; [h1 h2 h3 :as hands] (get-hands epoch 3)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h1 :hands hands)
        ;; _ (draw-tile* env (first hands) (find-tile-p game m-tile/monument?))
        ;; {:keys [game hands]} (refresh-game @conn game)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h2 :hands hands)
        ;; _ (draw-tile* env (first hands) (find-tile-p game m-tile/monument?))
        ;; {:keys [game hands]} (refresh-game @conn game)
        ;; {:keys [game hands]} (draws-bid-pass-pass env game :winner h3 :hands hands)
        ; _ (draw-tile* env (first hands) (find-tile-p game m-tile/ra?))


        ]
    (m-game/notify-all-clients! env (::game/id game))
    nil))

(defn reset [{:keys [::db/conn ::pathom/parser] :as env} game-short-id]
  (let [game (get-game @conn game-short-id)]
    (assert game)
    (parser {} [`(m-game/reset {::game/id ~(::game/id game)})])
    (m-game/notify-all-clients! env (::game/id game))
    nil))

(defn clear-bots! [{:keys [::db/conn ::pathom/parser] :as env}]
  (reset! (:listeners (meta conn)) {}))
