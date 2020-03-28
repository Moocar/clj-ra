(ns ra.model.game
  (:require [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [datascript.core :as d]
            [integrant.core :as ig]
            [ra.db :as db]
            [ra.model.tile :as m-tile]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]))

(pc/defmutation new-game [{:keys [::db/conn]} {:keys [::game/num-players]}]
  {::pc/params [::game/num-players]
   ::pc/output [::game/id]}
  (let [tile-bag (d/q '[:find [?t ...]
                        :where [?t ::tile/type]]
                      @conn)
        entity   {::game/id          (db/uuid)
                  ::game/num-players num-players
                  ::game/tile-bag    tile-bag}]
    (d/transact! conn [entity])
    entity))

(pc/defmutation join-game [{:keys [::db/conn]} {game-id ::game/id player-id ::player/-id}]
  {::pc/params [::game/id ::player/id]
   ::pc/output []}
  (d/transact! conn [[{::game/id [::game/id game-id]
                       ::game/players [[::player/id player-id]]}]])
  {})

(pc/defresolver game-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::game/id]}]
  {::pc/input #{::game/id}
   ::pc/output [::game/num-players
                {::game/tile-bag m-tile/q}
                ::game/id]}
  (d/pull (d/db conn) parent-query [::game/id id]))

(def resolvers
  [new-game game-resolver])

(defmethod ig/init-key ::ref-data [_ {:keys [::db/conn]}]
  (let [tiles (d/q '[:find ?t
                     :where [?t ::tile/type]]
                   @conn)]
    (when (empty? tiles)
      (d/transact! conn (m-tile/new-bag)))))
