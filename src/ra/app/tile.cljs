(ns ra.app.tile
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.semantic-ui.views.card.ui-card :refer [ui-card]]
            [ra.specs.tile :as tile]
            [com.fulcrologic.semantic-ui.elements.button.ui-button
             :refer
             [ui-button]]
            [com.fulcrologic.semantic-ui.elements.label.ui-label :refer [ui-label]]
            [com.fulcrologic.semantic-ui.views.card.ui-card-group
             :refer
             [ui-card-group]]))

(defn ui-clickable-sun-disk [{:keys [onClick value]}]
  (ui-button {:circular true
              :color    "brown"
              :key      value
              :onClick  (fn [_] (onClick))}
            (str value)))

(defn ui-sun-disk [{:keys [value used?]}]
  (ui-label {:circular true
             :color    (if used? "gray" "brown")
             :key      value}
            (str value)))

(defsc Tile [_ {:keys [::tile/title]}]
  {:query [::tile/id
           ::tile/title
           ::tile/disaster?
           ::tile/type]}
  (ui-card {:style {:height "50"
                    :width "50"}}
           title))

(def ui-tile (comp/factory Tile {:keyfn ::tile/id}))

(defn ui-tiles [tiles]
  (ui-card-group {} (map ui-tile tiles)))

(defn ui-blank-ra-spot []
  (ui-card {:style {:height "50"
                    :color "red"
                    :width "50"}}
           ""))
