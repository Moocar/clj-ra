(ns ra.app.score
  (:require [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.app.tile :as ui-tile]
            [ra.model.score :as m-score]
            [ra.specs.epoch-hand :as epoch-hand]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile.type :as tile-type]))

(defsc EpochHand [_ _]
  {:query [{::epoch-hand/hand [::hand/id
                               ::hand/available-sun-disks
                               ::hand/used-sun-disks
                               ::hand/score
                               {::hand/player [::player/name]}]}
           {::epoch-hand/tiles (comp/get-query ui-tile/Tile)}
           ::epoch-hand/epoch]})

(defn ui-epoch-headings [last-epoch?]
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
                     (dom/th {} "Total"))))

(defn ui-epoch-rows [state-map epoch-hands]
  (let [hand-scores (m-score/score-epoch epoch-hands)]
    (->> hand-scores
         #_(sort-by m-score/tally-hand)
         #_(reverse)
         (map (fn [hand-score]
                (let [tile-scores (:tile-scores hand-score)
                      hand        (get-in state-map [::hand/id (::hand/id (:hand hand-score))])
                      player      (get-in state-map (::hand/player hand))]
                  (dom/tr {}
                          (dom/td :.font-bold {} (::player/name player))
                          (dom/td {} (::tile-type/gold tile-scores))
                          (dom/td {} (::tile-type/god tile-scores))
                          (dom/td {} (::tile-type/civilization tile-scores))
                          (dom/td {} (::tile-type/river tile-scores))
                          (dom/td {} (::tile-type/pharoah tile-scores))
                          (dom/td {} (when-let [monument (::tile-type/monument tile-scores)]
                                       monument))
                          (dom/td {} (when-let [sun-disks (:sun-disk-scores hand-score)]
                                       (str sun-disks)))
                          (dom/td :.font-bold {} (m-score/tally-hand hand-score)))))))))

(defn ui-epoch [state-map epoch-hands]
  (let [last-epoch? (= 3 (::epoch-hand/epoch (first epoch-hands)))]
    (dom/table :.text-left {}
      (ui-epoch-headings last-epoch?)
      (dom/tbody {}
                 (ui-epoch-rows state-map epoch-hands)))))

(defn ui-last-epoch [state-map game-epoch-hands]
  (let [last-epoch? (some #(= 3 (::epoch-hand/epoch %)) game-epoch-hands)]
    (dom/table :.text-left {}
      (ui-epoch-headings last-epoch?)
      (dom/tbody {}
                 (->> game-epoch-hands
                      (group-by ::epoch-hand/epoch)
                      (sort-by first)
                      (mapcat (fn [[epoch epoch-hands]]
                                (concat
                                 [(dom/tr {} (dom/td :.pt-6.pb-2.underline {} (str "Epoch " epoch)))]
                                 (ui-epoch-rows state-map epoch-hands)
                                 ))))))))

(defn ui-final-scores [hands]
  (dom/table {}
    (dom/tbody {}
               (->> (m-score/order-hands-winning hands)
                    (map (fn [hand]
                           (dom/tr {}
                                   (dom/td :.font-bold {} (::player/name (::hand/player hand)))
                                   (dom/td {} (str (::hand/score hand))))))))))

(defn ui-modal [this {:keys [game close-prop]}]
  (let [state-map (app/current-state this)]
    (dom/div :.h-screen.w-screen.flex.justify-center.items-center.absolute.top-0.left-0.p-4 {}
      (dom/div :.absolute.top-0.left-0.w-screen.h-screen.z-10.bg-gray-500.opacity-75.z-10
        {:onClick (fn [] (m/set-value! this close-prop false))})
      (dom/div :.flex.flex-col.shadow-lg.rounded.bg-gray-50.border-2.p-2.mb-4.relative.w-screen.z-20
        {:style {:maxHeight "38rem"
                 :maxWidth  "32rem"}}
        (dom/div :.flex.w-full.justify-center {}
          (dom/div :.text-lg.font-bold.pb-2 {} "Epoch Finished"))
        (dom/div :.text.lg.font-bold.cursor-pointer.absolute.top-2.right-2
          {:onClick (fn [] (m/set-value! this close-prop false))} "Close")
        (dom/div :.flex.flex-col.overflow-y-scroll.overscroll-contain {}
          (ui-epoch state-map (game/get-current-epoch-hands game)))))))
