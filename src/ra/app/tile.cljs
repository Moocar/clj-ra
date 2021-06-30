(ns ra.app.tile
  (:require [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]
            [ra.app.ui :as ui]))

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
  (dom/button :.rounded-full.h-8.w-8.my-2.flex.items-center.justify-center.bg-blue-500
    {:onClick (fn [_] (onClick))}
    (str value)))

(defn ui-sun-disk [{:keys [value used?]}]
  (dom/div :.rounded-full.h-8.w-8.my-2.flex.items-center.justify-center
    {:classes (conj [] (if used? "bg-gray-500" "bg-green-500"))}
    (str value)))

(defsc Tile [this props {:keys [selectable? dimmed? on-click]}]
  {:query [::tile/id
           ::tile/title
           ::tile/disaster?
           ::tile/type
           ::tile/auction-track-position
           :ui/selected?]
   :ident ::tile/id}
  (dom/div :.w-24.h-24.flex.items-center.justify-center.rounded-md.border-2.justify-center.inline-block
              (cond-> {:style (cond-> {:backgroundColor (type-background (::tile/type props))}
                                dimmed?
                                (assoc :opacity "50%")
                                (:ui/selected? props)
                                (assoc :border "red solid 2px"))}
                (or selectable? on-click)
                (assoc :onClick (fn []
                                  (when selectable?
                                    (m/toggle! this :ui/selected?))
                                  (when on-click
                                    (on-click props)))))
              (dom/div {}
                (dom/span :.text-center.align-middle.inline-block (::tile/title props))
                (when (::tile/disaster? props)
                  (dom/span {:style {:color "RED"}} " X")))))

(def ui-tile (comp/factory Tile {:keyfn ::tile/id}))

(defn ui-tiles [tiles]
  (dom/div :.flex.space-x-2 {} (map ui-tile tiles)))

(defn ui-blank-ra-spot []
  (dom/div {:style {:height "50"
                    :color  "red"
                    :width  "50"}}
    ""))
