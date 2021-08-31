(ns ra.model.game
  (:require [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [ra.app.routing :as routing]
            [ra.specs.game :as game]
            [ra.specs.tile :as tile]
            [ra.specs.player :as player]))

(defn game-component []
  (comp/registry-key->class :ra.app.game/Game))

(defmutation join-game [_]
  (remote [_] true))

(defmutation new-game [_]
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game])))
  (ok-action [{:keys [result] :as env}]
    (let [game-id (get-in result [:body `new-game ::game/id])]
      (comp/transact! (:app env) [(join-game {::game/id   game-id
                                              ::player/id (get-in @(:state env) [:ui/current-player 1])})])
      (routing/to! (:app env) ["game" (str game-id)]))))

(defmutation start-game [_]
  (remote [_] true))

(defmutation leave-game [_]
  (remote [_] true)
  (ok-action [env]
    (swap! (:state env) assoc :ui/current-game nil)))

(defmutation reset [_]
  (remote [_] true))

(defmutation draw-tile [input]
  (remote [_] true))

(defmutation invoke-ra [_]
  (remote [_] true))

(defmutation bid [_]
  (remote [_] true))

(defn unselect-tile [state-map tile-id]
  (assoc-in state-map [::tile/id tile-id :ui/selected?] false))

(defmutation discard-disaster-tiles [input]
  (action [env]
    (swap! (:state env)
           (fn [s]
             (reduce unselect-tile s (:tile-ids input)))))
  (remote [_] true))

(defmutation use-god-tile [input]
  (action [env]
    (swap! (:state env) unselect-tile (:auction-track-tile-id input)))
  (remote [_] true))
