(ns ra.model.player
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [ra.specs.player :as player]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [clojure.spec.alpha :as s]))

(defn new-form-component []
  (comp/registry-key->class :ra.app.player/NewForm))

(defmutation new-player [input]
  (action [{:keys [state app]}]
          (swap! state
                 (fn [s]
                   (-> s
                       (merge/merge-component (new-form-component) input)
                       (assoc :ui/current-player [::player/id (::player/id input)])))))
  (remote [env] true))

(defmutation init-local-storage [_]
  (action [{:keys [app]}]
          (let [new-id (random-uuid)]
            (comp/transact! app [(new-player {::player/id new-id
                                            ::player/name ""})])
            (-> js/window .-localStorage (.setItem "player.id" new-id)))))

(defmutation use-local-storage-player [{:keys [player-id]}]
  (action [{:keys [state app]}]
          (df/load! app [::player/id player-id] (new-form-component)
                    {:post-action (fn [{:keys [result state]}]
                                    (let [{:keys [body]} result
                                          data (get body [::player/id player-id])]
                                      (if (::player/name data)
                                        (swap! state
                                               (fn [s]
                                                 (-> s
                                                     (merge/merge-component (new-form-component) data)
                                                     (assoc :ui/current-player [::player/id player-id]))))
                                        (comp/transact! app [(init-local-storage {})]))))})))

(defmutation save [input]
  (action [{:keys [state]}]
          (swap! state assoc-in [::player/id (::player/id input) ::player/name] (::player/name input)))
  (remote [env] true))
