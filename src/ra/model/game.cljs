(ns ra.model.game
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

(defmutation new-game [_]
  (action [{:keys [app state component]}]
          (js/console.log "new game"))
  (remote [env] true))
