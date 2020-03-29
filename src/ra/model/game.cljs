(ns ra.model.game
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.algorithms.data-targeting :as dt]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]))

(defn game-component []
  (comp/registry-key->class :ra.app.client/Game))

(defmutation new-game [_]
  (remote [{:keys [ast state] :as env}]
          (-> env
              (m/returning (game-component))
              (m/with-target [:current-game]))))
