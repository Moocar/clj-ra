(ns ra.model.player
  (:require [com.wsscode.pathom.connect :as pc]
            [ra.specs.player :as player]
            [ra.db :as db]
            [datascript.core :as d]))

(pc/defmutation new-player [{:keys [::db/conn]} {:keys [::player/name]}]
  {::pc/params [::player/name]
   ::pc/output [::player/id]}
  (let [entity {::player/id   (db/uuid)
                ::player/name name}]
    (d/transact! conn [entity])
    entity))

(def resolvers
  [new-player])
