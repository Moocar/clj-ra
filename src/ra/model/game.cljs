(ns ra.model.game
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.components :as comp]
            [ra.specs.game :as game]))

(defn game-component []
  (comp/registry-key->class :ra.app.game/Game))

(defmutation new-game [_]
  (remote [env] true
          (-> env
              (m/returning (game-component))
              (m/with-target [:ui/current-game])))
  (ok-action [{:keys [result] :as env}]
             (let [game-id (get-in result [:body `new-game ::game/id])]
               (-> js/window
                   .-history
                   (.pushState #js{:game-id game-id}
                               "Game in progress"
                               (str "/game/" (str game-id)))))))

(defmutation join-game [_]
  (remote [env] true
          (-> env
              (m/returning (game-component))
              (m/with-target [:ui/current-game]))))

(defmutation start-game [_]
  (remote [env] true
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
  (remote [{:keys [state] :as env}]
          (-> env
              (m/with-params (merge input {::game/id (get-in @state [:ui/current-game 1])}))
              (m/returning (game-component))
              (m/with-target [:ui/current-game]))))

(defmutation invoke-ra [_]
  (remote [{:keys [state] :as env}]
          (-> env
              (m/returning (game-component))
              (m/with-target [:ui/current-game]))))

(defmutation bid [_]
  (remote [{:keys [state] :as env}]
          (-> env
              (m/returning (game-component))
              (m/with-target [:ui/current-game]))))

(defmutation discard-disaster-tiles [input]
  (remote [{:keys [state] :as env}]
          (-> env
              (m/returning (game-component))
              (m/with-target [:ui/current-game]))))

(defmutation use-god-tile [input]
  (remote [{:keys [state] :as env}]
          (-> env
              (m/returning (game-component))
              (m/with-target [:ui/current-game]))))
