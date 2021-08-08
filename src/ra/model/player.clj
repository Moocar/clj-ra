(ns ra.model.player
  (:require [datascript.core :as d]
            [ra.specs.player :as player]
            [com.wsscode.pathom.connect :as pc]
            [ra.db :as db]
            [com.wsscode.pathom.core :as p]))

(def q
  [::player/name
   ::player/id])

(defn load-with-error [db q id]
  (let [response (d/pull db q [::player/id id])]
    (if (nil? response)
      (throw (ex-info "Player not found" {:user-error? true}))
      response)))

(pc/defresolver player-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::player/id]}]
  {::pc/input #{::player/id}
   ::pc/output q}
  (load-with-error @conn parent-query id))

(pc/defmutation new-player [{:keys [::db/conn]} {:keys [::player/id]}]
  {::pc/params #{::player/id}
   ::pc/output q}
  (let [{:keys [db-after]} (d/transact! conn [[:db/add -1 ::player/id id]])]
    (load-with-error db-after q id)))

(pc/defmutation save [{:keys [::db/conn ]} {:keys [::player/id ::player/name]}]
  {::pc/params #{::player/id}
   ::pc/output q}
  (let [{:keys [db-after]} (d/transact! conn [[:db/add [::player/id id] ::player/name name]])]
    (load-with-error db-after q id)))

(def resolvers
  [player-resolver

   new-player
   save])
