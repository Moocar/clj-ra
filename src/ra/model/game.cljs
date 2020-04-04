(ns ra.model.game
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.components :as comp]
            [ra.specs.game :as game]))

(defn game-component []
  (comp/registry-key->class :ra.app.client/Game))

(defmutation new-game [_]
  (remote [env] true
          (-> env
              (m/returning (game-component))
              (m/with-target [:current-game])))
  (ok-action [{:keys [result] :as env}]
             (let [game-id (get-in result [:body `new-game ::game/id])]
               (-> js/window
                   .-history
                   (.pushState #js{:game-id game-id}
                               "Ra in progress"
                               (str "/game/" (str game-id)))))))

(defmutation join-game [_]
  (remote [env] true
          (-> env
              (m/returning (game-component))
              (m/with-target [:current-game]))))

(defmutation start-game [_]
  (remote [env] true
          (-> env
              (m/returning (game-component))
              (m/with-target [:current-game]))))

(defmutation draw-tile [input]
  (remote [{:keys [state] :as env}]
          (-> env
              (m/with-params (merge input {::game/id (get-in @state [:current-game 1])}))
              (m/returning (game-component))
              (m/with-target [:current-game]))))
