(ns ra.app.help
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.app.tile :as ui-tile]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]
            [ra.app.sun-disk :as ui-sun-disk]
            [com.fulcrologic.fulcro.components :as comp]))

(defn ui-tiles-row [heading tiles description]
  (dom/div :.flex.flex-col.gap-2.pt-4 {}
    (dom/div :.font-bold {} heading)
    (dom/div :.flex.gap-2.flex-wrap {}
      (map (fn [tile]
             (ui-tile/ui-tile (comp/computed (dissoc tile :stack-size) (select-keys tile [:stack-size]))))
           tiles))
    description))

(defn ui-help-modal [this]
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center.absolute.top-0.left-0.p-4 {}
    (dom/div :.absolute.top-0.left-0.w-screen.h-screen.z-10.bg-gray-500.opacity-75.z-10
      {:onClick (fn [] (m/set-value! this :ui/show-help-modal false))})
    (dom/div :.flex.flex-col.shadow-lg.rounded.bg-gray-50.border-2.p-2.mb-4.relative.w-screen.h-full.z-20
      {:style {:maxHeight "38rem"
               :maxWidth  "32rem"}}
      (dom/div :.flex.w-full.justify-center {}
        (dom/div :.text-lg.font-bold.pb-2 {} "Help"))
      (dom/div :.text.lg.font-bold.cursor-pointer.absolute.top-2.right-2
        {:onClick (fn [] (m/set-value! this :ui/show-help-modal false))} "Close")
      (dom/div :.flex.flex-col.overflow-y-scroll.overscroll-contain {}
        (dom/div :.flex.flex-col.gap-2 {}
          (dom/div {} "Bottom right number is the number of tiles in the bag")
          (dom/div :.font-bold.text-xl {} "Special Tiles")
          (ui-tiles-row "Ra"
                        [{::tile/title "Ra" ::tile/type ::tile-type/ra :stack-size 30}]
                        (dom/div {}
                    "Triggers an auction. Put directly in Ra track. Discard at end of epoch."))

          (dom/hr {})
          (ui-tiles-row "Disasters"
                        [{::tile/type ::tile-type/monument ::tile/disaster? true ::tile/title "Earthquake" :stack-size 5}
                         {::tile/type ::tile-type/civilization ::tile/disaster? true ::tile/title "War" :stack-size 4}
                         {::tile/type ::tile-type/pharoah ::tile/title "Funeral" ::tile/disaster? true :stack-size 2}
                         {::tile/type ::tile-type/river ::tile/title "Drought" ::tile/disaster? true :stack-size 2}]
                        (dom/div {} "Disaster. Discard two of disaster's type. For Drought, discard floods before niles."))
          (dom/hr {})
          (dom/div :.font-bold.text-xl {} "Scored at end of Epoch")
          (ui-tiles-row "Gold"
                        [{::tile/title "Gold" ::tile/type ::tile-type/gold :stack-size 5}]
                        (dom/span {} (dom/span :.font-bold "3 points") ". Discard at end of epoch."))
          (dom/hr {})
          (ui-tiles-row "God"
                        [{::tile/title "God" ::tile/type ::tile-type/god :stack-size 8}]
                        (dom/span {} (dom/span :.font-bold "2 points") ". Or discard to take any auction tile as your action. Discard at end of epoch."))
          (dom/hr {})
          (ui-tiles-row "Civilization"
                        [{::tile/title "Art" ::tile/type ::tile-type/civilization :stack-size 5}
                         {::tile/title "Agriculture" ::tile/type ::tile-type/civilization :stack-size 5}
                         {::tile/title "Astronomy" ::tile/type ::tile-type/civilization :stack-size 5}
                         {::tile/title "Writing" ::tile/type ::tile-type/civilization :stack-size 5}
                         {::tile/title "Religion" ::tile/type ::tile-type/civilization :stack-size 5}
                         {::tile/type ::tile-type/civilization ::tile/disaster? true ::tile/title "War" :stack-size 4}]
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
                        [{::tile/title "Nile" ::tile/scarab? true ::tile/type ::tile-type/river :stack-size 25}
                         {::tile/title "Flood" ::tile/type ::tile-type/river :stack-size 12}
                         {::tile/type ::tile-type/river ::tile/title "Drought" ::tile/disaster? true :stack-size 2}]
                        (dom/span {} (dom/span :.font-bold "1 point each") ". Niles only count if you have at least one flood. Floods are discarded at end of epoch. In event of drought, floods must be discarded before niles."))
          (dom/hr {})
          (ui-tiles-row "Pharoahs"
                        [{::tile/title "Pharoah" ::tile/scarab? true ::tile/type ::tile-type/pharoah :stack-size 25}
                         {::tile/type ::tile-type/pharoah ::tile/title "Funeral" ::tile/disaster? true :stack-size 2}]
                        (dom/span {} (dom/span :.font-bold "5 points") " for players with most pharoahs. " (dom/span :.font-bold {} "-2 points") " for players with least."))
          (dom/hr {})
          (dom/div :.font-bold.text-xl.pt-8 {} "Scored at end of Game only")
          (ui-tiles-row "Monuments"
                        [{::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Fortress" :stack-size 5}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Obelisk" :stack-size 5}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Palace" :stack-size 5}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Pyramid" :stack-size 5}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Temple" :stack-size 5}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Statue" :stack-size 5}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Step Pyramid" :stack-size 5}
                         {::tile/type ::tile-type/monument ::tile/scarab? true ::tile/title "Sphinx" :stack-size 5}
                         {::tile/type ::tile-type/monument ::tile/disaster? true ::tile/title "Earthquake" :stack-size 5}]
                        (dom/div :.flex.flex-col {}
                          (dom/div {} (dom/span :.font-bold "1 point each"))
                          (dom/div {} (dom/span :.font-bold {} "5, 10 or 15 points" ) " per set of 3, 4, or 5. ")
                          (dom/div {} (dom/span :.font-bold {} "10, 15 points ") " for 7, 8 different types." )))
          (dom/hr {})
          (dom/div :.flex.flex-col.gap-2.pt-4 {}
            (dom/div :.font-bold {} "Sun Disks")
            (ui-sun-disk/ui {:value 13 :round true})
            (dom/div {} (dom/span :.font-bold "5 points") " for player with highest total.")
            (dom/div {} (dom/span :.font-bold "-5 points") " for player with lowest total.")))))))
