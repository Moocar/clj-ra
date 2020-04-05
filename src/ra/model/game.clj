(ns ra.model.game
  (:require [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [datascript.core :as d]
            [ghostwheel.core :as g]
            [integrant.core :as ig]
            [ra.date :as date]
            [ra.db :as db]
            [ra.core :refer [update-when remove-keys]]
            [ra.model.player :as m-player]
            [ra.model.tile :as m-tile]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]
            [com.fulcrologic.fulcro.networking.websocket-protocols :as fws-protocols]))

(def sun-disk-sets
  {2 [#{9 6 5 2}
      #{8 7 4 3}]
   3 [#{13 8 5 2}
      #{12 9 6 3}
      #{11 10 7 4}]
   4 [#{13 6 2}
      #{12 7 3}
      #{11 8 4}
      #{10 9 5}]
   5 [#{16 7 2}
      #{15 8 3}
      #{14 9 4}
      #{ 13 10 5}
      #{12 11 6}]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries

(defn find-all-tile-ids [db]
  (d/q '[:find [?t ...]
         :where [?t ::tile/type]]
       db))

(defn find-num-players [db game-id]
  (or (d/q '[:find (count ?pid) .
             :in $ ?game-id
             :where [?gid ::game/id ?game-id]
             [?gid ::game/players ?pid]]
           db
           game-id)
      0))

(defn find-current-player [db {:keys [::game/id]}]
  (d/q '[:find ?pid .
         :in $ ?game-id
         :where [?gid ::game/id ?game-id]
         [?gid ::game/current-epoch ?eid]
         [?eid ::epoch/current-hand ?hid]
         [?hid ::hand/player ?pid]]
       db id))

(defn sample-tile [db {:keys [::game/id]}]
  (first
   (d/q '[:find (sample 1 ?tid) .
          :in $ ?game-id
          :where [?gid ::game/id ?game-id]
          [?gid ::game/tile-bag ?tid]]
        db id)))

(defn player-turn? [db {player-id ::player/id :as input}]
  (= (::player/id (d/entity db (find-current-player db input))) player-id))

(defn find-current-hand [db {game-id ::game/id}]
  (get-in (d/entity db [::game/id game-id])
          [::game/current-epoch ::epoch/current-hand]))

(defn find-current-epoch [db {game-id ::game/id}]
  (get-in (d/entity db [::game/id game-id]) [::game/current-epoch]))

(defn strip-globals [q]
  (remove (fn [j]
            (and (map? j)
                 (vector? (key (first j)))
                 (= 2 (count (key (first j))))
                 (= '_ (second (key (first j))))))
          q))

(pc/defresolver game-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::game/id]}]
  {::pc/input #{::game/id}
   ::pc/output [{::game/tile-bag m-tile/q}
                {::game/players m-player/q}
                ::game/started-at
                {::game/current-epoch [::epoch/number
                                       ::epoch/current-sun-disk
                                       ::epoch/in-auction?
                                       {::epoch/last-ra-invokee [{::hand/player [::player/id
                                                                                 ::player/name]}]}
                                       {::epoch/current-hand [{::hand/player [::player/id
                                                                              ::player/name]}]}
                                       {::epoch/hands [{::hand/tiles [::tile/title]}
                                                       ::hand/available-sun-disks
                                                       ::hand/used-sun-disks
                                                       ::hand/my-go?
                                                       {::hand/player [::player/id]}]}]}
                ::game/id]}
  (let [result (d/pull @conn (strip-globals parent-query) [::game/id id])]
   (-> result
       ;; TODO add zdt encoding to websocket transit
       (update-when ::game/started-at str)
       (update ::game/current-epoch
               (fn [epoch]
                 (update epoch ::epoch/hands
                         (fn [hands]
                           (map (fn [hand]
                                  (assoc hand ::hand/my-go? (= (::hand/seat hand)
                                                               (::hand/seat (::epoch/current-hand epoch)))))
                                hands))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutations

(defn notify-other-clients [{:keys [connected-uids cid websockets]}]
  (let [other-cids (disj (:any @connected-uids) cid)]
    (doseq [o other-cids]
      (fws-protocols/push websockets o :refresh {:now :yes}))))

(pc/defmutation new-game [{:keys [::db/conn] :as env} _]
  {::pc/params []
   ::pc/output [::game/id]}
  (let [tile-bag (find-all-tile-ids @conn)
        entity   {::game/id          (db/uuid)
                  ::game/tile-bag    tile-bag}]
    (d/transact! conn [entity])
    (notify-other-clients env)
    entity))

(pc/defmutation join-game [{:keys [::db/conn]} {game-id ::game/id player-id ::player/id}]
  {::pc/params [::game/id ::player/id]
   ::pc/output [::game/id]}
  (if (>= (find-num-players @conn game-id) 5)
    (throw (ex-info "Maximum players already reached" {}))
    (d/transact! conn [[:db/add [::game/id game-id] ::game/players [::player/id player-id]]]))
  {::game/id game-id})

(pc/defmutation start-game [{:keys [::db/conn]} {game-id ::game/id}]
  {::pc/params [::game/id]
   ::pc/output [::game/id]}
  (if-let [started-at (::game/started-at (d/entity @conn [::game/id game-id]))]
    (throw (ex-info "Game already started" {:started-at started-at}))
    (let [num-players (find-num-players @conn game-id)]
      (if (< num-players 2)
        (throw (ex-info "Need at least two players" {}))
        (let [id-atom      (atom -1)
              player-hands (map (fn [player sun-disks dbid i]
                                  {::hand/available-sun-disks sun-disks
                                   ::hand/player              (:db/id player)
                                   ::hand/id                  (db/uuid)
                                   ::hand/seat                i
                                   :db/id                     dbid})
                                (::game/players (d/entity @conn [::game/id game-id]))
                                (shuffle (get #p sun-disk-sets #p num-players))
                                (repeatedly #(swap! id-atom dec))
                                (range))
              epoch        {::epoch/id               (db/uuid)
                            ::epoch/number           1
                            ::epoch/current-sun-disk 1
                            ::epoch/current-hand     (:db/id (first (shuffle player-hands)))
                            ::epoch/hands            (map :db/id player-hands)
                            :db/id                   (swap! id-atom dec)}]
          (d/transact! conn (concat
                             player-hands
                             [epoch]
                             [[:db/add [::game/id game-id] ::game/started-at (date/zdt)]
                              [:db/add [::game/id game-id] ::game/current-epoch (:db/id epoch)]]))))))
  {::game/id game-id})

;; TODO next is continue on draw normal tile, (maybe which player is next?) or focus on auctioning

(defn draw-ra-tx [db input tile]
  (let [epoch (find-current-epoch db input)
        hand  (find-current-hand db input)]
    [[:db/add (:db/id epoch) ::epoch/auction-tiles tile]
     [:db/retract [::game/id (::game/id input)] ::game/tile-bag tile]
     [:db/add (:db/id epoch) ::epoch/in-auction? true]
     [:db/add (:db/id epoch) ::epoch/last-ra-invokee (:db/id hand)]]))

(defn next-hand [hand]
  (let [db (d/entity-db hand)
        epoch (-> hand ::epoch/_current-hand first)
        game-id (-> epoch ::game/_current-epoch first ::game/id)
        num-players (find-num-players db game-id)]
   (if (< (inc (::hand/seat hand)) num-players)
     (d/entity db (d/q '[:find ?hid .
                         :in $ ?epoch-id ?seat-num
                         :where [?epoch-id ::epoch/hands ?hid]
                         [?hid ::hand/seat ?seat-num]]
                       db
                       (:db/id epoch)
                       (inc (::hand/seat hand))))
     (d/entity db (d/q '[:find ?hid .
                         :in $ ?epoch-id ?seat-num
                         :where [?epoch-id ::epoch/hands ?hid]
                         [?hid ::hand/seat ?seat-num]]
                       db
                       (:db/id epoch)
                       0)))))

#_(defn calc-next-hand [db epoch]
  (loop []
    (::hand/seat (::epoch/current-hand epoch))))

(defn draw-normal-tile-tx [db input tile]
  (let [epoch (find-current-epoch db input)
        hand (find-current-hand db input)]
    [[:db/add (:db/id hand) ::hand/tiles tile]
     [:db/retract [::game/id (::game/id input)] ::game/tile-bag tile]
     [:db/add (:db/id epoch) ::epoch/current-hand (:db/id (next-hand hand))]]))

(pc/defmutation draw-tile [{:keys [::db/conn]} {:keys [::game/id] :as input}]
  {::pc/params [::game/id ::player/id]
   ::pc/output [::game/id]}
  #p input
  (if (not (player-turn? @conn input))
    (throw (ex-info "not your turn" {}))
    (let [tile (sample-tile @conn input)]
      (if (= (::tile/type (d/entity @conn tile)) ::tile-type/ra)
        (d/transact! conn (draw-ra-tx @conn input tile))
        (d/transact! conn (draw-normal-tile-tx @conn input tile)))
      {::game/id id})))

(defmethod ig/init-key ::ref-data [_ {:keys [::db/conn]}]
  (let [tiles (d/q '[:find ?t
                     :where [?t ::tile/type]]
                   @conn)]
    (when (empty? tiles)
      (d/transact! conn (m-tile/new-bag)))))

(def resolvers
  [draw-tile
   join-game
   new-game
   start-game

   game-resolver])
