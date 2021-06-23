(ns ra.app.player
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [ra.specs.player :as player]
            [com.fulcrologic.fulcro.dom :as dom]))

(defsc Player [_ {:keys [::player/name]}]
  {:query [::player/name
           ::player/id]}
  (dom/strong name))

(def ui-player (comp/factory Player {:keyfn ::player/id}))
