(ns ra.model.game
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

#_(defmutation new-game [_]
  (action [{:keys [app state component]}]
          (let [new-id (rand-int)]
           (swap! state (fn [s]
                          (assoc-in s [::game/id ]))))))
