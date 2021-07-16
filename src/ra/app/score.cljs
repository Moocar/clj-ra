(ns ra.app.score
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.application :as app]
            [ra.specs.tile.type :as tile-type]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]))

(defn ui-table [s hand-scores]
  (dom/table :.text-left {}
    (dom/thead {}
               (dom/tr {}
                       (dom/th {} "")
                       (dom/th {} "Gold")
                       (dom/th {} "Gods")
                       (dom/th {} "Civs")
                       (dom/th {} "Rivers")
                       (dom/th {} "Pharoahs")
                       (dom/th {} "Epoch Total")))
    (dom/tbody {}
               (map (fn [hand-score]
                      (let [tile-scores (:tile-scores hand-score)
                            hand        (get-in s [::hand/id (::hand/id hand-score)])
                            player      (get-in s (::hand/player hand))]
                        (dom/tr {}
                                (dom/td :.font-bold {} (::player/name player))
                                (dom/td {} (::tile-type/gold tile-scores))
                                (dom/td {} (::tile-type/god tile-scores))
                                (dom/td {} (::tile-type/civilization tile-scores))
                                (dom/td {} (::tile-type/river tile-scores))
                                (dom/td {} (::tile-type/pharoah tile-scores))
                                (dom/td :.font-bold {} (+ (reduce + (vals tile-scores))
                                                          (or (:sun-disks hand-scores) 0))))))
                    hand-scores))))

(defn ui-modal [this {:keys [close-prop hand-scores]}]
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center.absolute.top-0.left-0.p-4 {}
    (dom/div :.absolute.top-0.left-0.w-screen.h-screen.z-10.bg-gray-500.opacity-75.z-10 {})
    (dom/div :.flex.flex-col.shadow-lg.rounded.bg-gray-50.border-2.p-2.mb-4.relative.w-screen.z-20
      {:style {:maxHeight "38rem"
               :maxWidth "32rem"}}
      (dom/div :.flex.w-full.justify-center {}
        (dom/div :.text-lg.font-bold.pb-2 {} "Epoch Finished"))
      (dom/div :.text.lg.font-bold.cursor-pointer.absolute.top-2.right-2
        {:onClick (fn [] (m/set-value! this close-prop false))} "Close")
      (dom/div :.flex.flex-col.overflow-y-scroll.overscroll-contain {}
        (ui-table (app/current-state this) hand-scores)))))
