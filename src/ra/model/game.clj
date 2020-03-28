(ns ra.model.game
  (:require [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [datascript.core :as d]
            [ghostwheel.core :as g]
            [integrant.core :as ig]
            [ra.date :as date]
            [ra.db :as db]
            [ra.model.player :as m-player]
            [ra.model.tile :as m-tile]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]))

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

(pc/defmutation new-game [{:keys [::db/conn]} _]
  {::pc/params []
   ::pc/output [::game/id]}
  (let [tile-bag (find-all-tile-ids @conn)
        entity   {::game/id          (db/uuid)
                  ::game/tile-bag    tile-bag}]
    (d/transact! conn [entity])
    entity))

(pc/defmutation join-game [{:keys [::db/conn]} {game-id ::game/id player-id ::player/id}]
  {::pc/params [::game/id ::player/id]
   ::pc/output []}
  (if (>= (find-num-players @conn game-id) 5)
    (throw (ex-info "Maximum players already reached" {}))
    (d/transact! conn [[:db/add [::game/id game-id] ::game/players [::player/id player-id]]]))
  {})

(pc/defmutation start-game [{:keys [::db/conn]} {game-id ::game/id}]
  {::pc/params [::game/id]
   ::pc/output []}
  (if-let [started-at (::game/started-at (d/entity @conn [::game/id game-id]))]
    (throw (ex-info "Game already started" {:started-at started-at}))
    (let [id-atom      (atom -1)
          num-players  (find-num-players @conn game-id)
          player-hands (map (fn [player sun-disks dbid i]
                              {::hand/sun-disks sun-disks
                               ::hand/player    (:db/id player)
                               ::hand/seat      i
                               :db/id           dbid})
                            (::game/players (d/entity @conn [::game/id game-id]))
                            (shuffle (get sun-disk-sets num-players))
                            (repeatedly #(swap! id-atom dec))
                            (range))
          epoch        {::epoch/number           1
                        ::epoch/current-sun-disk 1
                        ::epoch/current-hand     (:db/id (first (shuffle player-hands)))
                        ::epoch/hands            (map :db/id player-hands)
                        :db/id                   (swap! id-atom dec)}]
      (d/transact! conn (concat
                         player-hands
                         [epoch]
                         [[:db/add [::game/id game-id] ::game/started-at (date/zdt)]
                          [:db/add [::game/id game-id] ::game/current-epoch (:db/id epoch)]]))))
  {})

(defn find-current-player [db {:keys [::game/id]}]
  (d/q '[:find ?pid .
         :in $ ?game-id
         :where [?gid ::game/id ?game-id]
         [?gid ::game/current-epoch ?eid]
         [?eid ::epoch/current-hand ?hid]
         [?hid ::hand/player ?pid]]
       db id))

(defn player-turn? [db {player-id ::player/id :as input}]
  (= (::player/id (d/entity db (find-current-player db input))) player-id))

(pc/defmutation draw-tile [{:keys [::db/conn]} input]
  {::pc/params [::game/id ::player/id]
   ::pc/output []}
  (if (not (player-turn? @conn input))
    (throw (ex-info "not your turn" {}))
    {}))

(pc/defresolver game-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::game/id]}]
  {::pc/input #{::game/id}
   ::pc/output [{::game/tile-bag m-tile/q}
                {::game/players m-player/q}
                {::game/current-epoch [::epoch/number
                                       ::epoch/current-sun-disk
                                       {::epoch/current-hand [{::hand/player [::player/id]}]}
                                       {::epoch/hands [::hand/tiles
                                                       ::hand/sun-disks
                                                       {::hand/player [::player/id]}]}]}
                ::game/id]}
  (d/pull @conn parent-query [::game/id id]))

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
