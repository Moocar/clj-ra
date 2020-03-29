(ns ra.model.user
  (:require [datascript.core :as d]
            [ra.specs.user :as user]
            [com.wsscode.pathom.connect :as pc]
            [ra.db :as db]
            [com.wsscode.pathom.core :as p]))

(pc/defresolver user-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::user/id]}]
  {::pc/input #{::user/id}
   ::pc/output [::user/id ::user/name]}
  (println "pulling user" id)
  (d/pull @conn parent-query [::user/id id]))

(pc/defmutation new-user [{:keys [::db/conn]} {:keys [::user/id]}]
  {::pc/params #{::user/id}
   ::pc/output []}
  (d/transact! conn [{::user/id id}])
  {})

(pc/defmutation save [{:keys [::db/conn]} {:keys [::user/id ::user/name]}]
  {::pc/params #{::user/id}
   ::pc/output []}
  (d/transact! conn [[:db/add [::user/id id] ::user/name name]])
  {})

(def resolvers
  [user-resolver

   new-user
   save])
