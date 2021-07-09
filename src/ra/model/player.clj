(ns ra.model.player
  (:require [datascript.core :as d]
            [ra.specs.player :as player]
            [com.wsscode.pathom.connect :as pc]
            [ra.db :as db]
            [com.wsscode.pathom.core :as p]))

(def q
  [::player/name
   ::player/id])

(pc/defresolver player-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::player/id]}]
  {::pc/input #{::player/id}
   ::pc/output [::player/id ::player/name]}
  (d/pull @conn parent-query [::player/id id]))

(pc/defmutation new-player [{:keys [::db/conn]} {:keys [::player/id]}]
  {::pc/params #{::player/id}
   ::pc/output []}
  (d/transact! conn [{::player/id id}])
  {})

(pc/defmutation save [{:keys [::db/conn]} {:keys [::player/id ::player/name] :as input}]
  {::pc/params #{::player/id}
   ::pc/output []}
  (println "saving player" input)
  (d/transact! conn [[:db/add [::player/id id] ::player/name name]])
  {})

(def resolvers
  [player-resolver

   new-player
   save])
