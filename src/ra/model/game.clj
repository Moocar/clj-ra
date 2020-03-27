(ns ra.model.game
  (:require [com.wsscode.pathom.connect :as pc]
            [datascript.core :as d]
            [ra.db :as db]
            [ra.specs.game :as game]))

(pc/defmutation new-game [{:keys [::db/conn]} {:keys [::game/num-players]}]
  {::pc/params [::game/num-players]
   ::pc/output [::game/id]}
  (let [entity {::game/id          (db/uuid)
                ::game/num-players num-players
                }]
    (d/transact! conn [entity])
    entity))

(def resolvers
  [new-game])
