(ns ra.app.tile
  (:require [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.semantic-ui.elements.button.ui-button
             :refer
             [ui-button]]
            [com.fulcrologic.semantic-ui.elements.label.ui-label :refer [ui-label]]
            [com.fulcrologic.semantic-ui.views.card.ui-card :refer [ui-card]]
            [com.fulcrologic.semantic-ui.views.card.ui-card-group
             :refer
             [ui-card-group]]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]))

(def type-background
  {::tile-type/ra "red"
   ::tile-type/god "gold"
   ::tile-type/civilization "beige"
   ::tile-type/monument "cornflowerBlue"
   ::tile-type/gold "orange"
   ::tile-type/pharoah "green"
   ::tile-type/river "lightblue"
   ::tile-type/flood "lightblue"})

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

(defsc Tile [this props {:keys [selectable? dimmed?]}]
  {:query [::tile/id
           ::tile/title
           ::tile/disaster?
           ::tile/type
           :ui/selected?]
   :ident ::tile/id}
  (ui-card (cond-> {:style (cond-> {:height          "50"
                                    :backgroundColor (type-background (::tile/type props))
                                    :width           "50"}
                             dimmed?
                             (assoc :opacity "50%")
                             (:ui/selected? props)
                             (assoc :border "red solid 2px"))}
             selectable?
             (assoc :onClick (fn []
                               (m/toggle! this :ui/selected?))))
           (dom/div {}
             (dom/span (::tile/title props))
             (when (::tile/disaster? props)
               (dom/span {:style {:color "RED"}} " X")))))

(def ui-tile (comp/factory Tile {:keyfn ::tile/id}))

(defn ui-tiles [tiles]
  (ui-card-group {} (map ui-tile tiles)))

(defn ui-blank-ra-spot []
  (ui-card {:style {:height "50"
                    :color "red"
                    :width "50"}}
           ""))
