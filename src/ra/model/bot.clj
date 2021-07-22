(ns ra.model.bot
  (:require [clojure.set :as set]
            [com.wsscode.pathom.connect :as pc]
            [datascript.core :as d]
            [ra.db :as db]
            [ra.model.game :as m-game]
            [ra.model.player :as m-player]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.specs.auction.reason :as auction-reason]
            [ra.specs.auction :as auction]))

(defn bid! [{:keys [:parser] :as env} epoch hand game]
  (let [available-sun-disks (::hand/available-sun-disks hand)
        auction             (::epoch/auction epoch)
        voluntary-auction?  (and (= hand (::epoch/last-ra-invoker epoch))
                                (= ::auction-reason/invoke (::auction/reason auction)))
        sun-disk            (if (and (not voluntary-auction?)
                                     (zero? (rand-int 2)))
                              nil
                              (rand-nth (vec available-sun-disks)))]
    (parser env [`(m-game/bid {::hand/id ~(::hand/id hand)
                               ::game/id ~(::game/id game)
                               :sun-disk ~sun-disk})])))

(defn discard-disaster-tiles! [{:keys [:parser] :as env} hand]
  (let [tiles                  (set (::hand/tiles hand))
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
    (parser env [`(m-game/discard-disaster-tiles {::hand/id ~(::hand/id hand)
                                                  :tile-ids ~(map ::tile/id selected-tiles)})])))

(defn invoke-ra! [{:keys [:parser] :as env} hand]
  (parser env [`(m-game/invoke-ra {::hand/id ~(::hand/id hand)})]))

(defn draw-tile! [{:keys [:parser] :as env} hand game]
  (parser env [`(m-game/draw-tile {::hand/id ~(::hand/id hand)
                                   ::game/id ~(::game/id game)})]))

(defn handle-change [{:keys [::db/conn] :as env} player-id game-id]
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
            (Thread/sleep 500)
            (if (::epoch/auction epoch)
              (if (= (count (::auction/bids (::epoch/auction epoch)))
                     (count (::game/players game)))
                nil
                (bid! env epoch current-hand game))
              (if (::epoch/in-disaster? epoch)
                (discard-disaster-tiles! env current-hand)
                (if (epoch/auction-tiles-full? epoch)
                  (invoke-ra! env current-hand)
                  (if (= 0 (rand-int 4))
                    (invoke-ra! env current-hand)
                    (draw-tile! env current-hand game)))))
            (let [game (m-game/load-game @conn m-game/game-query game-id)]
              (m-game/notify-clients (:websockets env) (:any @(:connected-uids env)) game))))))))

(defn rand-char []
  (char (+ 65 (rand-int 26))))

(defn new-bot-name []
  (apply str (concat ["Bot "] (repeatedly 4 rand-char))))

(pc/defmutation add-to-game
    [{:keys [::db/conn :parser] :as env} input]
  {::pc/params    #{::game/id}
   ::pc/transform m-game/notify-other-clients-transform
   ::pc/output    [::game/id]}
  (let [player-id   (db/uuid)
        player-name (new-bot-name)
        game        (d/entity @conn [::game/id (::game/id input)])]
    (parser env [`(m-player/new-player {::player/id ~player-id})])
    (parser env [`(m-player/save {::player/id   ~player-id
                                 ::player/name ~player-name})])
    (d/listen! conn
               player-id
               (fn [tx-report]
                 (try
                   (when (= (::game/id game) (::game/id (:tx-meta tx-report)))
                     (handle-change env
                                    player-id
                                    (::game/id game)))
                   (catch Exception e
                     (println e)
                     e))))
    (parser env [`(m-game/join-game {::player/id ~player-id
                                    ::game/id   ~(::game/id game)})])
    {::game/id (::game/id input)}))

(def resolvers
  [add-to-game])
