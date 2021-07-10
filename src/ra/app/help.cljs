(ns ra.app.help
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.app.tile :as ui-tile]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]))

(defn ui-help-modal [this]
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center.absolute.top-0.left-0.p-4 {}
    (dom/div :.absolute.top-0.left-0.w-screen.h-screen.z-10.bg-gray-500.opacity-75.z-10 {})
    (dom/div :.flex.flex-col.shadow-lg.rounded.bg-gray-50.border-2.p-2.mb-4.relative.w-screen.h-full.z-20
      {:style {:maxHeight "38rem"
               :maxWidth "32rem"}}
      (dom/div :.flex.w-full.justify-center {}
        (dom/div :.text-lg.font-bold.pb-2 {} "Help"))
      (dom/div :.text.lg.font-bold.cursor-pointer.absolute.top-2.right-2
        {:onClick (fn [] (m/set-value! this :ui/show-help-modal false))} "Close")
      (dom/div :.flex.flex-col.overflow-y-scroll.overscroll-contain {}
        (dom/div :.flex.flex-col.gap-2 {}
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title "Ra"
                              ::tile/type  ::tile-type/ra})
            (dom/div {}
              "Triggers an auction. Put directly in Ra track. Discard at end of epoch."))
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title "Gold"
                              ::tile/type  ::tile-type/gold})
            (dom/div {}
              (dom/span {} (dom/span :.font-bold "3 points") ". Discard at end of epoch.")))
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title "God"
                              ::tile/type  ::tile-type/god})
            (dom/div {}
              (dom/span {} (dom/span :.font-bold "2 points") ". Or discard to take any auction tile as your action. Discard at end of epoch.")))
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title "Art"
                              ::tile/type  ::tile-type/civilization})
            (dom/div :.flex.flex-col {}
              (dom/div {}
                "Points awarded for number of unique civilizations. Discard at end of epoch.")
              (dom/table :.text-left {}
                (dom/thead {}
                           (dom/tr {}
                                   (dom/th "# Unique Civs")
                                   (dom/th "Points")))
                (dom/tbody {}
                           (dom/tr {}
                                   (dom/td "0")
                                   (dom/td "-5"))
                           (dom/tr {}
                                   (dom/td "1, 2")
                                   (dom/td "0"))
                           (dom/tr {}
                                   (dom/td "3")
                                   (dom/td "5"))
                           (dom/tr {}
                                   (dom/td "4")
                                   (dom/td "10"))
                           (dom/tr {}
                                   (dom/td "5")
                                   (dom/td "15"))))))
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title "Flood"
                              ::tile/type  ::tile-type/river})
            (dom/div {}
              (dom/span {} (dom/span :.font-bold "1 point each") ". In event of drought, floods must be discarded before niles. Discard at end of epoch.")))
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title   "Nile"
                              ::tile/scarab? true
                              ::tile/type    ::tile-type/river})
            (dom/div {}
              (dom/span (dom/span :.font-bold "1 point each") " if you have at least one flood.")))
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title "Pharoah"
                              ::tile/scarab? true
                              ::tile/type  ::tile-type/pharoah})
            (dom/div {}
              (dom/span {} (dom/span :.font-bold "5 points") " for players with most pharoahs. " (dom/span :.font-bold {} "-2 points") " for players with least.")))
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title "Temple"
                              ::tile/scarab? true
                              ::tile/type  ::tile-type/monument})
            (dom/div :.flex.flex-col {}
              (dom/div {} (dom/span :.font-bold "1 point each"))
              (dom/div {} (dom/span :.font-bold {} "5, 10 or 15 points" ) " per set of 3, 4, or 5. ")
              (dom/div {} (dom/span :.font-bold {} "10, 15 points ") " for 7, 8 different types." )))
          (dom/div :.flex.gap-2 {}
            (ui-tile/ui-tile {::tile/title     "Earthquake"
                              ::tile/disaster? true
                              ::tile/type      ::tile-type/monument})
            (dom/div {} "Disaster. Discard two of disaster's type. For Drought, discard floods before niles.")))))))
