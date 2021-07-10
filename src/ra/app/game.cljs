(ns ra.app.game
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [ra.app.epoch :as ui-epoch]
            [ra.app.event :as ui-event]
            [ra.app.hand :as ui-hand]
            [ra.app.help :as ui-help]
            [ra.app.player :as ui-player]
            [ra.app.sun-disk :as ui-sun-disk]
            [ra.app.tile :as ui-tile]
            [ra.app.ui :as ui]
            [ra.model.bot :as m-bot]
            [ra.model.game :as m-game]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]))

(declare Game)

(defn joined? [game]
  (first (filter #(= (::player/id %) (::player/id (:ui/current-player game)))
                 (::game/players game))))

(defn menu-bar [this {:keys [game epoch]}]
  (assert game)
  (dom/div :.border-b-2.flex.justify-between.pb-2 {}
    (dom/div :.flex {}
      (dom/div :.pw-2 {}
        (dom/span {} "Game: ")
        (dom/span {} (::game/short-id game)))
      (dom/div {}
        (dom/span :.pl-8 {} "Epoch: ")
        (dom/span (::epoch/number epoch))))
    (dom/div :.flex.gap-2 {}
      (ui/button {:onClick (fn []
                             (m/set-value! this :ui/show-help-modal true))}
        "Help")
      (ui/button {:onClick (fn []
                             (dr/change-route! this ["lobby"])
                             (.pushState (.-history js/window) #js {} "" "/lobby"))}
        "Leave Game")
      )))

(defmutation back-to-lobby [{}]
  (action [env]
    (swap! (:state env) assoc :ui/current-game nil)))

(defn ui-unstarted [this game]
  (dom/div :.p-2 {}
    (menu-bar this {:game game})
    (dom/div :.flex-col.justify-center {}
      (dom/div :.flex.flex-col.justify-center.shadow-md.rounded.bg-gray-50.px-8.pt-6.pb-8.mb-4.items-center.gap-6 {}
        (dom/div :.p-2 {} "Game has not started yet")
        (when-not (joined? game)
          (ui/button {:onClick (fn []
                                 (comp/transact! this [(m-game/join-game {::game/id   (::game/id game)
                                                                          ::player/id (::player/id (:ui/current-player game))})]))}
            "Join Game"))
        (ui/button {:onClick (fn []
                               (comp/transact! this [(m-bot/add-to-game {::game/id   (::game/id game)
                                                                         ::player/id (::player/id (:ui/current-player game))})]))}
          "Add Bot")
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
                                   (dr/change-route! this ["lobby"])
                                   (.pushState (.-history js/window) #js {} "" "/lobby"))}
              "Leave Game")))))
    (dom/div {}
      (dom/div :.font-bold  {} "Event log")
      (ui-event/ui-items (reverse (::game/events game))))))

(def players->ra-count
  {2 6
   3 8
   4 9
   5 10})

(defn ui-ra-track [{:keys [hands ra-tiles] :as props}]
  (let [blank-spots (- (players->ra-count (count hands))
                       (count ra-tiles))]
    (dom/div :.flex.flex-row.flex-wrap.gap-2 {}
      (concat (map (fn [_] (dom/div
                             {:style {"animation-name"     "drawtile"
                                      "animation-duration" "1s"
                                      "transform"          "scale(1, 1)"}}
                             (ui-tile/ra-tile)))
                   ra-tiles)
              (map (fn [_] (ui-tile/blank-ra-tile))
                   (range blank-spots))))))

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

(defn ui-action-bar [this props]
  (dom/div :.h-16.flex.justify-center.items-center {}
    (if (and (:my-go? props) (not (::epoch/auction (:epoch props))))
      (dom/div :.flex.flex-row.space-x-2 {}
        (ui-tile-bag this props)
        (ui-invoke-ra this props)
        (when (::epoch/in-disaster? (:epoch props))
          (ui-discard-disaster-tiles this props)))
      (if (and (:my-go? props) (::epoch/auction (:epoch props)))
        (dom/div :.font-bold {}
          (dom/span {} "Your bid"))
        (dom/div :.font-bold {}
          (dom/span {} "Waiting for ")
          (dom/span {} (::player/name (::hand/player (:hand props)))))))))

(defn ui-main-game [this {:keys [game epoch] :as props}]
  (dom/div :.flex.flex-col.p-2.gap-2 {}
    (menu-bar this props)
    (dom/div :.flex-col.w-screen {}
      (dom/div :.font-bold {} "Auction Count")
      (ui-ra-track props))
    (dom/div :.flex-col.w-screen {}
      (dom/div :.font-bold {} "Tiles")
      (ui-epoch/ui-auction-track this props))
    (dom/div :.flex.items-center {}
      (dom/div :.font-bold {} "Current Bid Disk")
      (dom/div :.pl-4 {} (ui-sun-disk/ui {:value (::epoch/current-sun-disk epoch)})))
    (dom/hr {})
    (ui-action-bar this props)
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
           :ui/show-help-modal
           {[:ui/current-player '_] (comp/get-query ui-player/Player)}
           ::game/id]
   :ident ::game/id
   :initial-state {}
   :componentDidMount (fn [this]
                        (set! (.-title js/document) (str "Game " (::game/short-id (comp/props this)) (str " | Ra?"))))
   :route-segment ["game" ::game/id]
   :will-enter (fn [_ props]
                 (dr/route-immediate [::game/id (uuid (::game/id props))]))
   :allow-route-change? (fn [this] false)
   :route-denied (fn [this router relative-path]
                   (when (js/confirm "Are you sure you want to leave the game?")
                     (dr/retry-route! this router relative-path )))}
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
                     (assoc p :my-go? (my-go? p)))))
    (when (:ui/show-help-modal props)
      (ui-help/ui-help-modal this))))

(def ui-game (comp/factory Game {:keyfn ::game/id}))
