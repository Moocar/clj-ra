(ns ra.model.game
  (:require [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [ra.app.routing :as routing]
            [ra.specs.game :as game]
            [ra.specs.tile :as tile]))

(defn game-component []
  (comp/registry-key->class :ra.app.game/Game))

(defmutation new-game [_]
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game])))
  (ok-action [{:keys [result] :as env}]
    (let [game-id (get-in result [:body `new-game ::game/id])]
      (routing/to! (:app env) ["game" (str game-id)]))))

(defmutation join-game [_]
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game]))))

(defmutation start-game [_]
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game]))))

(defmutation leave-game [_]
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game])))
  (ok-action [env]
    (swap! (:state env) assoc :ui/current-game nil)))

(defmutation reset [_]
  (remote [env] true
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game]))))

(defmutation draw-tile [input]
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game]))))

(defmutation invoke-ra [_]
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game]))))

(defmutation bid [_]
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game]))))

(defn unselect-tile [state-map tile-id]
  (assoc-in state-map [::tile/id tile-id :ui/selected?] false))

(defmutation discard-disaster-tiles [input]
  (action [env]
    (swap! (:state env)
           (fn [s]
             (reduce unselect-tile s (:tile-ids input)))))
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game]))))

(defmutation use-god-tile [input]
  (action [env]
    (swap! (:state env) unselect-tile (:auction-track-tile-id input)))
  (remote [env]
    (-> env
        (m/returning (game-component))
        (m/with-target [:ui/current-game]))))
