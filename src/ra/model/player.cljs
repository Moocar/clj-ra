(ns ra.model.player
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [ra.specs.player :as player]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [clojure.spec.alpha :as s]))

(defn player-component []
  (comp/registry-key->class :ra.app.client/PlayerDetails))

(defmutation new-player [input]
  (action [{:keys [state app]}]
          (js/console.log "new player" input)
          (swap! state
                 (fn [s]
                   (-> s
                       (merge/merge-component (player-component) input)
                       (assoc :current-player [::player/id (::player/id input)])))))
  (remote [env] true))

(defmutation init-local-storage [_]
  (action [{:keys [app]}]
          (js/console.log "init local storage")
          (let [new-id (random-uuid)]
            (comp/transact! app [(new-player {::player/id new-id
                                            ::player/name ""})])
            (-> js/window .-localStorage (.setItem "player.id" new-id)))))

(defmutation use-local-storage-player [{:keys [player-id]}]
  (action [{:keys [state app]}]
          (js/console.log "got player id" player-id)
          (df/load! app [::player/id player-id] (player-component)
                    {:post-action (fn [{:keys [result state]}]
                                    (let [{:keys [body]} result]
                                      (if (::player/name body)
                                        (swap! state
                                               (fn [s]
                                                 (-> s
                                                     (merge/merge-component (player-component) body)
                                                     (assoc :current-player [::player/id (::player/id body)]))))
                                        (comp/transact! app [(init-local-storage {})]))))})))

(defmutation save [_]
  (remote [env] true))

#_(defmutation create-player [_]
  (action [{:keys [app]}]
          (let [new-id (random-uuid)]
            (merge/merge-component!
             app
             (comp/registry-key->class :ra.app.client/PlayerDetails)
             {::player/id   new-id
              ::player/name ""}
             :replace [:current-player])
            (-> js/window .-localStorage (.setItem "player.id" new-id)))))
