(ns ra.app.tile
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]))

(def type-classes
  {::tile-type/ra ["bg-red-500" "text-white"]
   ::tile-type/god ["bg-yellow-300"]
   ::tile-type/civilization ["bg-yellow-50"]
   ::tile-type/monument ["bg-blue-500" "text-white"]
   ::tile-type/gold ["bg-yellow-500" "text-white"]
   ::tile-type/pharoah ["bg-green-700" "text-white"]
   ::tile-type/river ["bg-blue-300"]
   ::tile-type/flood ["bg-blue-300"]})

(defn ra-tile []
  (dom/div :.w-8.h-8.md:w-20.md:h-20.flex.items-center.justify-center.border-2.rounded-md.inline-block.cursor-default.relative.shadow-md.flex-shrink-0
    (cond-> {:classes (get type-classes ::tile-type/ra)})
    (dom/span :.text-center.align-middle.inline-block.text-sm.z-10 "Ra")))

(defn blank-ra-tile []
  (dom/div :.w-8.h-8.md:w-20.md:h-20.flex.items-center.justify-center.border-2.rounded-md.inline-block.cursor-default.relative.shadow-md.flex-shrink-0 {}))

(defsc Tile [this props {:keys [selectable? dimmed? on-click stack-size most-pharoahs]}]
  {:query [::tile/id
           ::tile/title
           ::tile/disaster?
           ::tile/type
           ::tile/scarab?
           ::tile/auction-track-position
           ::tile/river-type
           ::tile/civilization-type
           ::tile/monument-type
           :ui/selected?]
   :ident ::tile/id}
  (dom/div :.md:w-20.md:h-20.flex.items-center.justify-center.border-2.rounded-md.inline-block.cursor-default.relative.shadow-md.flex-shrink-0
    (cond-> {:classes (concat (type-classes (::tile/type props))
                              (cond dimmed?               ["opacity-50"]
                                    (:ui/selected? props) ["border-2" "border-red-700" "cursor-pointer"])
                              (when selectable? ["cursor-pointer"]))}
      (or selectable? on-click)
      (assoc :onClick (fn []
                        (when selectable?
                          (m/toggle! this :ui/selected?))
                        (when on-click
                          (on-click props)))))
    (dom/div {}
      (dom/span :.text-center.align-middle.inline-block.text-sm.z-10.px-2.md:px-0.py-1.md:py-0 (::tile/title props))
      (when (::tile/disaster? props)
        (dom/span :.text-red-500.absolute.top-0.text-2xl.md:text-7xl.opacity-50.md:text-red {:classes ["inset-x-2/4" "md:left-4"]} " X"))
      (when stack-size
        (dom/div :.inline-block.md:absolute.bottom-0.right-0.pr-1 {}
          (str stack-size)))
      (when (::tile/scarab? props)
        (dom/div :.absolute.bottom-0.left-0.pl-1.hidden.md:block {}
          "\u267E"))
      (when (and (tile/pharoah? props) most-pharoahs)
        (dom/div :.absolute.top-0.right-0.pr-1.hidden.md:block {}
          "\u03A3")))))

(def ui-tile (comp/factory Tile {:keyfn ::tile/id}))

(defn ui-tiles [tiles]
  (dom/div :.flex.flex-row.flex-wrap.gap-2 {} (map ui-tile tiles)))
