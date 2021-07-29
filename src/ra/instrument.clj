(ns ra.instrument
  (:require [datascript.core :as d]
            [ra.db :as db]
            [ra.model.game :as m-game]
            [ra.pathom :as pathom]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.tile :as tile]))

(defn get-hands [game hand-count]
  (->> (::game/hands game)
       (sort-by ::hand/seat)
       (repeat 2)
       (apply concat)
       (drop-while #(not= (:db/id (::game/current-hand game)) (:db/id %)))
       (take hand-count)))

(defn get-game [db short-id]
  (d/entity db [::game/short-id short-id]))

(defn refresh-game [db game]
  (let [game (get-game db (::game/short-id game))
        hands (get-hands game 3)]
    {:game game
     :hands hands}))

(defn find-tile-p [game pred]
  (first (filter pred (::game/tile-bag game))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Actions

(defn draw-tile* [{:keys [::db/conn ::pathom/parser] :as env} game hand tile]
  (let [db @conn
        hand (d/entity db (:db/id hand))
        game (d/entity db (:db/id game))]
    (m-game/do-draw-tile env game hand tile)))

(defn bid [{:keys [::pathom/parser]} hand game sun-disk]
  (parser {} [`(m-game/bid {::hand/id ~(::hand/id hand)
                            ::game/id ~(::game/id game)
                            :sun-disk ~sun-disk})]))

(defn rand-bid [env hand game]
  (bid env hand game (first (::hand/available-sun-disks hand))))

(defn pass-bid [env hand game]
  (bid env hand game nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scenarios

(defn draw-tile [{:keys [::db/conn] :as env} game hand tile]
  (draw-tile* env game hand tile)
  (refresh-game @conn game))

(defn seat->hand [{:keys [game first-seat] :as env} seat]
  (assert (< seat (game/player-count game)))
  (let [target-seat (mod (+ first-seat seat) (game/player-count game))]
    (->> (::game/hands game)
         (sort-by ::hand/seat)
         (repeat 2)
         (apply concat)
         (drop-while (fn [hand] (not= (::hand/seat hand) target-seat)))
         (first))))

(defmulti do-action (fn [game hand action-type options] action-type))

(defn safe-tile [tile]
  (and (not (tile/disaster? tile))
       (not (tile/ra? tile))))

(defmethod do-action :draw
  [{:keys [game] :as env} hand _ {:keys [tile]}]
  (let [tile-pred (case tile
                    :safe (fn [tile] (and (not (tile/disaster? tile))
                                          (not (tile/ra? tile))))
                    tile)]
    (draw-tile* env game hand (find-tile-p game tile-pred))))

(defmethod do-action :bid
  [{:keys [game] :as env} hand _ {:keys [sun-disk]}]
  (case sun-disk
    :pass (pass-bid env hand game)
    :rand (rand-bid env hand game)
    (throw (ex-info "unknown bid option" {:sun-disk sun-disk}))))

(defn run-playbook [{:keys [::db/conn] :as env} game playbook]
  (let [env (assoc env
                   :first-seat (::hand/seat (::game/current-hand game)))]
    (loop [game                    game
           [action & next-actions] playbook]
      (let [env                        (assoc env :game game)
            [seat action-type options] action
            hand                       (seat->hand env seat)]
        (do-action (assoc env :game game) hand action-type options)
        (when (seq next-actions)
          (recur (get-game @conn (::game/short-id game))
                 next-actions))))
    (m-game/notify-all-clients! env (::game/id game))))

(defn all-bids-pass [seats]
  (concat (map (fn [seat] [seat :draw {:tile :safe}]) (butlast seats))
          [[(last seats) :draw {:tile tile/ra?}]]
          [[(first seats) :bid {:sun-disk :rand}]]
          (map (fn [seat] [seat :bid {:sun-disk :pass}]) (rest seats))))

(def test-playbook
  (concat
   (all-bids-pass [0 1 2 3])
   (all-bids-pass [0 1 2 3])
   (all-bids-pass [0 1 2 3])
   (all-bids-pass [1 2 3])
   (all-bids-pass [1 2 3])
   (all-bids-pass [1 2 3])
   (all-bids-pass [2 3])
   (all-bids-pass [2 3])
   [[2 :draw {:tile :safe}]
    [3 :draw {:tile :safe}]
    [2 :draw {:tile :safe}]
    [3 :draw {:tile :safe}]
    [2 :draw {:tile :safe}]
    [3 :draw {:tile :safe}]
    [2 :draw {:tile :safe}]
    [3 :draw {:tile :safe}]]))

(defn run [env game-short-id playbook]
  (run-playbook env (get-game @(::db/conn env) game-short-id) playbook))

(defn reset [{:keys [::db/conn ::pathom/parser] :as env} game-short-id]
  (let [game (get-game @conn game-short-id)]
    (assert game)
    (parser {} [`(m-game/reset {::game/id ~(::game/id game)})])
    (m-game/notify-all-clients! env (::game/id game))
    nil))

(defn clear-bots! [{:keys [::db/conn ::pathom/parser] :as env}]
  (reset! (:listeners (meta conn)) {}))
