(ns ra.model.game
  (:require [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [datascript.core :as d]
            [integrant.core :as ig]
            [ra.db :as db]
            [ra.model.tile :as m-tile]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.model.player :as m-player]
            [ghostwheel.core :as g]
            [ra.date :as date]))

(pc/defmutation new-game [{:keys [::db/conn]} _]
  {::pc/params []
   ::pc/output [::game/id]}
  (let [tile-bag (d/q '[:find [?t ...]
                        :where [?t ::tile/type]]
                      @conn)
        entity   {::game/id          (db/uuid)
                  ::game/tile-bag    tile-bag}]
    (d/transact! conn [entity])
    entity))

(defn find-num-players [conn game-id]
  (d/q '[:find (count ?pid) .
         :in $ ?game-id
         :where [?gid ::game/id ?game-id]
         [?gid ::game/players ?pid]]
       @conn
       game-id))

(pc/defmutation join-game [{:keys [::db/conn]} {game-id ::game/id player-id ::player/id}]
  {::pc/params [::game/id ::player/id]
   ::pc/output []}
  (if (>= (find-num-players conn game-id) 5)
    (throw (ex-info "Maximum players already reached" {}))
    (d/transact! conn [[:db/add [::game/id game-id] ::game/players [::player/id player-id]]]))
  {})

(pc/defmutation start-game [{:keys [::db/conn]} {game-id ::game/id}]
  {::pc/params [::game/id]
   ::pc/output []}
  (if-let [started-at (::game/started-at (d/entity @conn [::game/id game-id]))]
    (throw (ex-info "Game already started" {:started-at started-at}))
    (d/transact! conn [[:db/add [::game/id game-id] ::game/started-at (date/zdt)]]))
  {})

(pc/defresolver game-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::game/id]}]
  {::pc/input #{::game/id}
   ::pc/output [{::game/tile-bag m-tile/q}
                {::game/players m-player/q}
                ::game/id]}
  (d/pull @conn parent-query [::game/id id]))

(defmethod ig/init-key ::ref-data [_ {:keys [::db/conn]}]
  (let [tiles (d/q '[:find ?t
                     :where [?t ::tile/type]]
                   @conn)]
    (when (empty? tiles)
      (d/transact! conn (m-tile/new-bag)))))

(def resolvers
  [join-game
   new-game
   start-game

   game-resolver])
