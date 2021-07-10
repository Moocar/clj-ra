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
            [ra.specs.tile :as tile]))

(defn handle-change [{:keys [::db/conn :parser] :as env} player-id game-id]
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
                (parser env [`(m-game/bid {::hand/id ~(::hand/id current-hand)
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
                  (parser env [`(m-game/discard-disaster-tiles {::hand/id ~(::hand/id current-hand)
                                                                       :tile-ids ~(map ::tile/id selected-tiles)})]))
                (if (m-game/auction-tiles-full? epoch)
                  (parser env [`(m-game/invoke-ra {::hand/id ~(::hand/id current-hand)})])
                  (parser env [`(m-game/draw-tile {::hand/id ~(::hand/id current-hand)})]))))
            (m-game/notify-clients (:websockets env) (:any @(:connected-uids env)) game-id)))))))

(defn rand-char []
  (char (+ 65 (rand-int 26))))

(defn new-bot-name []
  (apply str (concat ["Bot "] (repeatedly 4 rand-char))))

(pc/defmutation add-to-game
    [{:keys [::db/conn :parser] :as env} input]
  {::pc/params    #{::game/id}
   ::pc/transform m-game/notify-other-clients
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
