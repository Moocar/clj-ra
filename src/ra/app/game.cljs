(ns ra.app.game
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [ra.app.epoch :as ui-epoch]
            [ra.app.event :as ui-event]
            [ra.app.hand :as ui-hand]
            [ra.app.player :as ui-player]
            [ra.app.sun-disk :as ui-sun-disk]
            [ra.app.tile :as ui-tile]
            [ra.app.ui :as ui]
            [ra.model.game :as m-game]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.app.routing :as routing]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]))

(declare Game)

(defn joined? [game]
  (first (filter #(= (::player/id %) (::player/id (:ui/current-player game)))
                 (::game/players game))))

(defn menu-bar [{:keys [game epoch]}]
  (assert game)
  (dom/div :.border-b-2.flex {}
    (dom/div :.pw-2 {}
      (dom/span {} "Game: ")
      (dom/span {} (::game/short-id game))
      (dom/span :.pl-8 {} "Epoch: ")
      (dom/span (::epoch/number epoch)))))

(defmutation back-to-lobby [{}]
  (action [env]
    (swap! (:state env) assoc :ui/current-game nil)))

(defn ui-unstarted [this game]
  (dom/div {}
    (menu-bar {:game game})
    (dom/div :.flex-col.justify-center {}
      (dom/div :.flex.flex-col.justify-center.shadow-md.rounded.bg-gray-50.px-8.pt-6.pb-8.mb-4.items-center.gap-6 {}
        (dom/div :.p-2 {} "Game has not started yet")
        (when-not (joined? game)
          (ui/button {:onClick (fn []
                                 (comp/transact! this [(m-game/join-game {::game/id   (::game/id game)
                                                                          ::player/id (::player/id (:ui/current-player game))})]))}
            "Join Game"))
        (when-not (joined? game)
          (ui/button {:onClick (fn []
                                 (comp/transact! this [(back-to-lobby {})])
                                 (.back js/history))}
            "Back"))
        (when (joined? game)
          (dom/div {}
            (ui/button {:onClick (fn []
                                   (comp/transact! this [(m-game/start-game (select-keys game [::game/id]))]))}
              "Start Game")))
        (when (joined? game)
          (dom/div {}
            (ui/button {:onClick (fn []
                                   (comp/transact! this [(m-game/leave-game {::game/id   (::game/id game)
                                                                             ::player/id (::player/id (:ui/current-player game))})])
                                   (routing/route-to! "/lobby"))}
              "Leave Game")))))
    (dom/div {}
      (dom/div :.font-bold  {} "Event log")
      (ui-event/ui-items (reverse (::game/events game))))))

(def players->ra-count
  {2 6
   3 8
   4 9
   5 10})

(defn ui-ra-track [{:keys [hands epoch ra-tiles] :as props}]
  (let [blank-spots (- (players->ra-count (count hands))
                       (count ra-tiles))]
    (dom/div :.flex.flex-row.flex-initial.w-screen.gap-2 {}
      (map (fn [ra-tile]
             (dom/div :.border-2.flex.items-center.justify-center.rounded-md.cursor-default.justify-self-auto.w-8.h-8
               (if ra-tile
                 {:classes (ui-tile/type-classes (::tile/type ra-tile))}
                 {})))
           (concat ra-tiles
                   (map (fn [_] nil) (range blank-spots)))))))

(defn my-go? [{:keys [hand my-player]}]
  (= (::player/id (::hand/player hand))
     (::player/id my-player)))

(defn ui-tile-bag [this {:keys [epoch hand auction my-go?]}]
  (ui/button
    (if (and my-go?
             (not auction)
             (not (::epoch/in-disaster? epoch))
             (not (ui-epoch/auction-tiles-full? epoch)))
      {:onClick (fn []
                  (comp/transact! this [(m-game/draw-tile {::hand/id (::hand/id hand)})]))}
      {:disabled true})
    "Draw Tile"))

(defn ui-invoke-ra [this {:keys [epoch hand auction my-go?]}]
  (ui/button
    (if (and my-go?
             (not auction)
             (not (::epoch/in-disaster? epoch)))
      {:onClick (fn []
                  (comp/transact! this [(m-game/invoke-ra {::hand/id (::hand/id hand)})]))}
      {:disabled true})
    "Invoke Auction"))

(defn ui-discard-disaster-tiles [this {:keys [hand my-go?]}]
  (filter ::tile/disaster? (::hand/tiles hand))
  (ui/button
    (if (and my-go?
             (seq (filter ::tile/disaster? (::hand/tiles hand))))
      {:onClick (fn []
                  (comp/transact! this [(m-game/discard-disaster-tiles
                                         {::hand/id (::hand/id hand)
                                          :tile-ids (map ::tile/id (filter :ui/selected? (::hand/tiles hand)))})]))}
      {:disabled true})
    "Discard Disasters"))

(defn ui-hands [this {:keys [auction epoch hands my-player] :as props}]
  (dom/div {}
    (->> (concat hands hands)
         (drop-while (fn [hand]
                       (not= (::player/id (::hand/player hand))
                             (::player/id my-player))))
         (take (count hands))
         (map (fn [hand]
                (ui-hand/ui-hand
                 (if auction
                   (comp/computed hand (assoc props
                                              :onClickSunDisk (fn [sun-disk]
                                                                (comp/transact! this [(m-game/bid {::hand/id (::hand/id hand) :sun-disk sun-disk})]))
                                              :highest-bid    (ui-epoch/highest-bid auction)))
                   (comp/computed hand (assoc props
                                              :click-god-tile (fn [hand tile]
                                                                (if (:ui/selected-god-tile epoch)
                                                                  (m/set-value! this :ui/selected-god-tile nil)
                                                                  (comp/transact! this [(ui-epoch/select-god-tile {:hand hand
                                                                                                                   :epoch epoch
                                                                                                                   :tile tile})]))))))))))))

(defn ui-status [{:keys [hand my-go?]}]
  (dom/div :.font-bold {}
    (if my-go?
      (dom/div {}
        (dom/span (str "It's your turn ")))
      (dom/div {}
        (dom/span {} "Waiting for ")
        (dom/span {} (::player/name (::hand/player hand)))))))

(defn ui-main-game [this {:keys [game epoch] :as props}]
  (dom/div :.flex.flex-col.p-2.gap-2 {}
    (menu-bar props)
    (dom/div :.flex-col.w-screen {}
      (dom/div :.font-bold {} "Auctions Invoked")
      (ui-ra-track props))
    (dom/div :.flex-col.w-screen {}
      (dom/div :.font-bold {} "Auction")
      (ui-epoch/ui-auction-track this props))
    (dom/div :.flex.items-center {}
      (dom/div :.font-bold {} "Current Sun Disk")
      (dom/div :.pl-4 {} (ui-sun-disk/ui {:value (::epoch/current-sun-disk epoch)})))
    (dom/hr {})
    (dom/div :.flex.flex-col {}
      (dom/div :.py-2 {}
        (ui-status props))
      (dom/div :.flex.flex-row.space-x-2.pb-2 {}
        (ui-tile-bag this props)
        (ui-invoke-ra this props)
        (ui-discard-disaster-tiles this props)))
    (dom/hr {})
    (dom/div {}
      (dom/h3 :.font-bold "Seats")
      (dom/div {}
        (ui-hands this props)))
    (dom/div :.flex-col.w-screen {}
      (dom/h3 :.font-bold "Events")
      (ui-event/ui-items (reverse (::game/events game))))))

(defsc Game [this props]
  {:query [{::game/players (comp/get-query ui-player/Player)}
           {::game/current-epoch (comp/get-query ui-epoch/Epoch)}
           {::game/events (comp/get-query ui-event/Item)}
           ;;           {::game/tile-bag (comp/get-query Tile)}
           ::game/started-at
           ::game/short-id
           {[:ui/current-player '_] (comp/get-query ui-player/Player)}
           ::game/id]
   :ident ::game/id
   :route-segment ["game" ::game/id]
   :will-enter (fn [app props]
                 (dr/route-immediate [::game/id (::game/id props)]))}
  (dom/div :.w-screen.bg-white {}
   (cond
     (not (::game/started-at props))
     (ui-unstarted this props)
     :else
     (ui-main-game this
                   (let [game props
                         epoch (::game/current-epoch game)
                         p {:game     game
                            :my-player   (:ui/current-player game)
                            :ra-tiles (::epoch/ra-tiles epoch)
                            :epoch    epoch
                            :hand     (::epoch/current-hand epoch)
                            :hands    (::epoch/hands epoch)
                            :auction  (::epoch/auction epoch)}]
                     (assoc p :my-go? (my-go? p)))))))

(def ui-game (comp/factory Game {:keyfn ::game/id}))
