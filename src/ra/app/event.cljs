(ns ra.app.event
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [ra.specs.game.event :as event]
            [ra.specs.game :as game]))

(defsc Item [_ props]
  {:query [::event/id
           ::event/description]
   :ident ::event/id}
  (dom/div {}
    (::event/description props)))

(def ui-item (comp/factory Item {:keyfn ::event/id}))

(defn ui-items [events]
  (dom/div :.flex-col.overflow-hidden.h-24 {}
           (reverse (map ui-item (take 4 events)))))
