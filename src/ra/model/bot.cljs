(ns ra.model.bot
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [ra.model.game :as m-game]))

(defmutation add-to-game [_]
  (remote [env]
          (m/returning env (m-game/game-component))))
