(ns ra.model.user
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [ra.specs.user :as user]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [clojure.spec.alpha :as s]))

(defn user-component []
  (comp/registry-key->class :ra.app.client/UserDetails))

(defmutation use-local-storage-user [{:keys [user-id]}]
  (action [{:keys [state app]}]
          (js/console.log "got user id" user-id)
          (df/load! app [::user/id user-id] (user-component)
                    {:target [:current-user]})))

(defmutation new-user [input]
  (action [{:keys [state app]}]
          (js/console.log "new user" input)
          (swap! state
                 (fn [s]
                   (-> s
                       (merge/merge-component (user-component) input)
                       (assoc :current-user [::user/id (::user/id input)])))))
  (remote [env] true))

(defmutation init-local-storage [_]
  (action [{:keys [app]}]
          (js/console.log "init local storage")
          (let [new-id (random-uuid)]
            (comp/transact! app [(new-user {::user/id new-id
                                            ::user/name ""})])
            (-> js/window .-localStorage (.setItem "user.id" new-id)))))

(defmutation save [_]
  (remote [env] true))

#_(defmutation create-user [_]
  (action [{:keys [app]}]
          (let [new-id (random-uuid)]
            (merge/merge-component!
             app
             (comp/registry-key->class :ra.app.client/UserDetails)
             {::user/id   new-id
              ::user/name ""}
             :replace [:current-user])
            (-> js/window .-localStorage (.setItem "user.id" new-id)))))
