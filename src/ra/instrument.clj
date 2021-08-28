(ns ra.instrument
  (:require [clojure.pprint :as pprint]
            [datascript.core :as d]
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

(defn seat->hand [{:keys [game first-seat] :as env} seat]
  (assert (< seat (game/player-count game)))
  (let [target-seat (mod (+ first-seat seat) (game/player-count game))]
    (->> (::game/hands game)
         (sort-by ::hand/seat)
         (repeat 2)
         (apply concat)
         (drop-while (fn [hand] (not= (::hand/seat hand) target-seat)))
         (first))))

(defn override-sun-disks [{:keys [::db/conn] :as env} game specs]
  (let [tx (mapcat (fn [[seat sun-disks]]
                     (let [hand (seat->hand env seat)]
                       (concat
                        [[:db/retract (:db/id hand) ::hand/available-sun-disks]]
                        (map (fn [sun-disk]
                               [:db/add (:db/id hand) ::hand/available-sun-disks sun-disk])
                             sun-disks))))
                   specs)]
    (d/transact! conn tx)
    (d/entity @conn (:db/id game))))

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

(defmethod do-action :use-god-tile
  [{:keys [::pathom/parser game]} hand _ {:keys [tile]}]
  (assert (seq (::game/auction-tiles game)))
  (let [tile-pred (case tile
                    :safe (fn [tile] (and (not (tile/disaster? tile))
                                          (not (tile/god? tile))))
                    tile)
        tile      (first (filter tile-pred (::game/auction-tiles game)))
        god-tile  (first (filter tile/god? (::hand/tiles hand)))]
    (assert god-tile)
    (assert tile)
    (parser {} [`(m-game/use-god-tile {::hand/id              ~(::hand/id hand)
                                       ::game/id              ~(::game/id game)
                                       :god-tile-id           ~(::tile/id tile)
                                       :auction-track-tile-id ~(::tile/id tile)})])))

(defmethod do-action :bid
  [{:keys [game] :as env} hand _ {:keys [sun-disk]}]
  (case sun-disk
    :pass (pass-bid env hand game)
    :rand (rand-bid env hand game)
    (if (number? sun-disk)
      (bid env hand game sun-disk)
      (throw (ex-info "unknown bid option" {:sun-disk sun-disk})))))

(defmethod do-action :print-hand
  [{:keys [game] :as env} hand _ {:keys []}]
  (pprint/pprint (d/touch (d/entity @(::db/conn env) (:db/id hand)))))

(defmethod do-action :print-game
  [{:keys [game] :as env} hand _ {:keys []}]
  (pprint/pprint (d/touch (d/entity @(::db/conn env) (:db/id game)))))

(defmethod do-action :invoke-ra
  [{:keys [::pathom/parser game]} hand _ _]
  (parser {} [`(m-game/invoke-ra {::hand/id ~(::hand/id hand)
                                  ::game/id ~(::game/id game)})]))

(defmethod do-action :discard
  [{:keys [::pathom/parser game]} hand _ {:keys [tiles]}]
  (let [tiles    (loop [selected   #{}
                        hand-tiles (set (::hand/tiles hand))
                        tile-preds tiles]
                (if (empty? tile-preds)
                  selected
                  (let [found (first (filter (first tile-preds) hand-tiles))]
                    (assert found)
                    (recur (conj selected found)
                           (disj hand-tiles found)
                           (rest tile-preds)))))
        tile-ids (map ::tile/id tiles)]
    (parser {} [`(m-game/discard-disaster-tiles {::hand/id ~(::hand/id hand)
                                                 ::game/id ~(::game/id game)
                                                 :tile-ids ~tile-ids})])))

(defn run-playbook [{:keys [::db/conn] :as env} game playbook]
  (assert (::game/started-at game))
  (let [env (assoc env
                   :first-seat (::hand/seat (::game/current-hand game)))]
    (loop [game                    game
           [action & next-actions] playbook]
      (let [env                        (assoc env :game game)
            [seat action-type options] action
            hand                       (seat->hand env seat)]
        (try
          (do-action (assoc env :game game) hand action-type options)
          (catch Exception e
            (let [data (ex-data e)
                  msg (ex-message e)]
              (throw (ex-info msg (assoc data :action action) e)))))
        (when (seq next-actions)
          (recur (get-game @conn (::game/short-id game))
                 next-actions))))
    (m-game/notify-all-clients! env (::game/id game))))

(defn all-bids-pass [seats]
  (concat (map (fn [seat] [seat :draw {:tile :safe}]) (butlast seats))
          [[(last seats) :draw {:tile tile/ra?}]]
          [[(first seats) :bid {:sun-disk :rand}]]
          (map (fn [seat] [seat :bid {:sun-disk :pass}]) (rest seats))))

(def step-pyramid
  [[0 :draw {:tile (fn [tile] (= (::tile/title tile) "Step Pyramid"))}]
   [1 :draw {:tile (fn [tile] (= (::tile/title tile) "Step Pyramid"))}]
   [0 :draw {:tile :safe}]
   [1 :draw {:tile :safe}]
   [0 :draw {:tile :safe}]
   [1 :draw {:tile :safe}]
   [0 :draw {:tile :safe}]
   [1 :draw {:tile :safe}]
   [0 :invoke-ra {}]
   [1 :bid {:sun-disk :pass}]
   [0 :bid {:sun-disk :rand}]])

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
    [3 :invoke-ra {}]
    [2 :bid {:sun-disk :rand}]
    [3 :bid {:sun-disk :pass}]

    [3 :draw {:tile :safe}]
    [3 :invoke-ra {}]
    [3 :bid {:sun-disk :rand}]

    [3 :draw {:tile :safe}]
    [3 :invoke-ra {}]
    [3 :bid {:sun-disk :rand}]

    [3 :draw {:tile tile/disaster?}]
    [3 :invoke-ra {}]
    ]))

(def end-of-epoch
  [[0 :draw {:tile tile/civ?}]
   [1 :draw {:tile tile/pharoah?}]
   [0 :draw {:tile tile/ra?}]
   [1 :bid {:sun-disk :pass}]
   [0 :bid {:sun-disk :rand}]
   ;; [1 :draw {:tile tile/civ?}]
   ;; [0 :draw {:tile tile/pharoah?}]
   [1 :draw {:tile tile/ra?}]
   [0 :bid {:sun-disk :pass}]
   [1 :bid {:sun-disk :rand}]
   [0 :draw {:tile tile/ra?}]
   [1 :bid {:sun-disk :pass}]
   [0 :bid {:sun-disk :pass}]
   [1 :draw {:tile tile/ra?}]
   [0 :bid {:sun-disk :pass}]
   [1 :bid {:sun-disk :pass}]
   [0 :draw {:tile tile/ra?}]
   [1 :bid {:sun-disk :pass}]
   [0 :bid {:sun-disk :pass}]
   [1 :draw {:tile tile/ra?}]])

(def end-of-game
  (let [two-full-ras [[0 :draw {:tile tile/ra?}]
                      [1 :bid {:sun-disk :pass}]
                      [0 :bid {:sun-disk :pass}]
                      [1 :draw {:tile tile/ra?}]
                      [0 :bid {:sun-disk :pass}]
                      [1 :bid {:sun-disk :pass}]]]
    (concat

     ;; Epoch 2
     two-full-ras
     two-full-ras
     [[0 :draw {:tile tile/ra?}]
      [1 :bid {:sun-disk :pass}]
      [0 :bid {:sun-disk :pass}]
      [1 :draw {:tile tile/ra?}]]

     ;; Epoch 2
     two-full-ras
     two-full-ras
     [[0 :draw {:tile tile/ra?}]
      [1 :bid {:sun-disk :pass}]
      [0 :bid {:sun-disk :pass}]
      [1 :draw {:tile tile/ra?}]]

     ;; Epoch 3
     two-full-ras
     two-full-ras
     [[0 :draw {:tile tile/ra?}]
      [1 :bid {:sun-disk :pass}]
      [0 :bid {:sun-disk :pass}]
      [1 :draw {:tile tile/ra?}]])))

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

(comment
  ;; See dev/user.clj
  )
