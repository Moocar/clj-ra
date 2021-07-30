(ns ra.app.help
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.app.tile :as ui-tile]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]
            [ra.app.sun-disk :as ui-sun-disk]))

(defn ui-tiles-row [heading tiles description]
  (dom/div :.flex.flex-col.gap-2.pt-4 {}
    (dom/div :.font-bold {} heading)
    (dom/div :.flex.gap-2.flex-wrap {}
      (map ui-tile/ui-tile tiles))
    description))

(defn ui-help-modal [this]
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center.absolute.top-0.left-0.p-4 {}
    (dom/div :.absolute.top-0.left-0.w-screen.h-screen.z-10.bg-gray-500.opacity-75.z-10 {})
    (dom/div :.flex.flex-col.shadow-lg.rounded.bg-gray-50.border-2.p-2.mb-4.relative.w-screen.h-full.z-20
      {:style {:maxHeight "38rem"
               :maxWidth  "32rem"}}
      (dom/div :.flex.w-full.justify-center {}
        (dom/div :.text-lg.font-bold.pb-2 {} "Help"))
      (dom/div :.text.lg.font-bold.cursor-pointer.absolute.top-2.right-2
        {:onClick (fn [] (m/set-value! this :ui/show-help-modal false))} "Close")
      (dom/div :.flex.flex-col.overflow-y-scroll.overscroll-contain {}
        (dom/div :.flex.flex-col.gap-2 {}
          (dom/div :.font-bold.text-xl {} "Special Tiles")
          (ui-tiles-row "Ra"
                        [{::tile/title "Ra" ::tile/type ::tile-type/ra}]
                        (dom/div {}
                    "Triggers an auction. Put directly in Ra track. Discard at end of epoch."))

          (dom/hr {})
          (ui-tiles-row "Disasters"
                        [{::tile/title "Earthquake" ::tile/disaster? true ::tile/type ::tile-type/monument}
                         {::tile/type ::tile-type/civilization ::tile/disaster? true ::tile/title "War"}
                         {::tile/type ::tile-type/pharoah ::tile/title "Funeral" ::tile/disaster? true}
                         {::tile/type ::tile-type/river ::tile/title "Drought" ::tile/disaster? true}]
                        (dom/div {} "Disaster. Discard two of disaster's type. For Drought, discard floods before niles."))
          (dom/hr {})
          (dom/div :.font-bold.text-xl {} "Scored at end of Epoch")
          (ui-tiles-row "Gold"
                        [{::tile/title "Gold" ::tile/type ::tile-type/gold}]
                        (dom/span {} (dom/span :.font-bold "3 points") ". Discard at end of epoch."))
          (dom/hr {})
          (ui-tiles-row "God"
                        [{::tile/title "God" ::tile/type ::tile-type/god}]
                        (dom/span {} (dom/span :.font-bold "2 points") ". Or discard to take any auction tile as your action. Discard at end of epoch."))
          (dom/hr {})
          (ui-tiles-row "Art"
                        [{::tile/title "Art" ::tile/type ::tile-type/civilization}
                         {::tile/title "Agriculture" ::tile/type ::tile-type/civilization}
                         {::tile/title "Astronomy" ::tile/type ::tile-type/civilization}
                         {::tile/title "Writing" ::tile/type ::tile-type/civilization}
                         {::tile/title "Religion" ::tile/type ::tile-type/civilization}
                         {::tile/type ::tile-type/civilization ::tile/disaster? true ::tile/title "War"}]
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
          (dom/hr {})
          (ui-tiles-row "Rivers"
                        [{::tile/title "Flood" ::tile/type ::tile-type/river}
                         {::tile/title "Nile" ::tile/scarab? true ::tile/type ::tile-type/river}
                         {::tile/type ::tile-type/river ::tile/title "Drought" ::tile/disaster? true}]
                        (dom/span {} (dom/span :.font-bold "1 point each") ". Niles only count if you have at least one flood. Floods are discarded at end of epoch. In event of drought, floods must be discarded before niles."))
          (dom/hr {})
          (ui-tiles-row "Pharoahs"
                        [{::tile/title "Pharoah" ::tile/scarab? true ::tile/type ::tile-type/pharoah}
                         {::tile/type ::tile-type/pharoah ::tile/title "Funeral" ::tile/disaster? true}]
                        (dom/span {} (dom/span :.font-bold "5 points") " for players with most pharoahs. " (dom/span :.font-bold {} "-2 points") " for players with least."))
          (dom/hr {})
          (dom/div :.font-bold.text-xl.pt-8 {} "Scored at end of Game only")
          (ui-tiles-row "Monuments"
                        [{::tile/title "Temple" ::tile/scarab? true ::tile/type ::tile-type/monument}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Fortress"}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Obelisk"}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Palace"}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Pyramid"}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Temple"}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Statue"}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Step Pyramid"}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Sphinx"}
                         {::tile/type ::tile-type/monument ::tile/disaster? true ::tile/title "Earthquake"}]
                        (dom/div :.flex.flex-col {}
                          (dom/div {} (dom/span :.font-bold "1 point each"))
                          (dom/div {} (dom/span :.font-bold {} "5, 10 or 15 points" ) " per set of 3, 4, or 5. ")
                          (dom/div {} (dom/span :.font-bold {} "10, 15 points ") " for 7, 8 different types." )))
          (dom/hr {})
          (dom/div :.flex.flex-col.gap-2.pt-4 {}
            (dom/div :.font-bold {} "Sun Disks")
            (ui-sun-disk/ui {:value 13 :round true})
            (dom/div {} (dom/span :.font-bold "5 points") " with highest total. " (dom/span :.font-bold "-5 points") " for player with lowest")))))))
