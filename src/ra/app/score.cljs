(ns ra.app.score
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.application :as app]
            [ra.specs.tile.type :as tile-type]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.game :as game]
            [ra.specs.epoch :as epoch]))

(defn ui-table [s hand-scores game]
  (let [last-epoch? (::game/finished-at game)]
    (dom/table :.text-left {}
      (dom/thead {}
                 (dom/tr {}
                         (dom/th {} "")
                         (dom/th {} "Gold")
                         (dom/th {} "Gods")
                         (dom/th {} "Civs")
                         (dom/th {} "Rivers")
                         (dom/th {} "Pharoahs")
                         (when last-epoch?
                           (dom/th {} "Monuments"))
                         (when last-epoch?
                           (dom/th {} "Sun Disks"))
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
                                  (when last-epoch?
                                    (dom/td {} (::tile-type/monument tile-scores)))
                                  (when last-epoch?
                                    (dom/td {} (str (:sun-disks hand-score))))
                                  (dom/td :.font-bold {} (+ (reduce + (vals tile-scores))
                                                            (or (:sun-disks hand-scores) 0))))))
                      hand-scores)))))

(defn ui-final-scores [hands]
  (dom/table {}
    (dom/tbody {}
               (->> hands
                    (sort-by ::hand/score)
                    reverse
                    (map (fn [hand]
                           (dom/tr {}
                                   (dom/td :.font-bold {} (::player/name (::hand/player hand)))
                                   (dom/td {} (str (::hand/score hand))))))))))

(defn ui-modal [this {:keys [game close-prop hand-scores]}]
  (let [state-map (app/current-state this)]
    (dom/div :.h-screen.w-screen.flex.justify-center.items-center.absolute.top-0.left-0.p-4 {}
      (dom/div :.absolute.top-0.left-0.w-screen.h-screen.z-10.bg-gray-500.opacity-75.z-10 {})
      (dom/div :.flex.flex-col.shadow-lg.rounded.bg-gray-50.border-2.p-2.mb-4.relative.w-screen.z-20
        {:style {:maxHeight "38rem"
                 :maxWidth  "32rem"}}
        (dom/div :.flex.w-full.justify-center {}
          (dom/div :.text-lg.font-bold.pb-2 {} (if (::game/finished-at game)
                                                 "Game Finished"
                                                 "Epoch Finished")))
        (dom/div :.text.lg.font-bold.cursor-pointer.absolute.top-2.right-2
          {:onClick (fn [] (m/set-value! this close-prop false))} "Close")
        (dom/div :.flex.flex-col.overflow-y-scroll.overscroll-contain {}
          (ui-table state-map hand-scores game)
          (when (::game/finished-at game)
            (dom/div :.flex.flex-col.w-full.justify-center {}
              (dom/div :.text-lg.font-bold.pb-2.text-center.pt-4 {} "Final scores")
              (ui-final-scores (::epoch/hands (::game/current-epoch game))))))))))
