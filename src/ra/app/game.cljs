(ns ra.app.game
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [ra.app.event :as ui-event]
            [ra.app.hand :as ui-hand]
            [ra.app.help :as ui-help]
            [ra.app.player :as ui-player]
            [ra.app.score :as ui-score]
            [ra.app.sun-disk :as ui-sun-disk]
            [ra.app.tile :as ui-tile]
            [ra.app.ui :as ui]
            [ra.model.bot :as m-bot]
            [ra.model.game :as m-game]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.specs.game.event :as event]
            [ra.specs.game.event.type :as event-type]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [ra.app.routing :as routing]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.app.audio :as audio]
            [com.fulcrologic.fulcro.application :as app]))

(declare Game)

(defn joined? [game]
  (first (filter #(= (::player/id %) (::player/id (:ui/current-player game)))
                 (::game/players game))))

(defn menu-bar [this {:keys [game]}]
  (assert game)
  (dom/div :.border-b-2.flex.justify-between.pb-2 {}
    (dom/div :.flex {}
      (dom/div :.pw-2 {}
        (dom/span {} "Game: ")
        (dom/span {} (::game/short-id game)))
      (dom/div {}
        (dom/span :.pl-8 {} "Epoch: ")
        (dom/span (::game/epoch game))))
    (dom/div :.flex.gap-2 {}
      (ui/button {:onClick (fn []
                             (m/set-value! this :ui/show-help-modal true))}
        "Help")
      (ui/button {:onClick (fn [] (routing/to! this ["lobby"]))}
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
                                   (routing/to! this ["lobby"]))}
              "Leave Game")))))
    (dom/div {}
      (dom/div :.font-bold  {} "Event log")
      (ui-event/ui-items (reverse (::game/events game))))))

(defn ui-ra-track [{:keys [hands ra-tiles] :as props}]
  (let [blank-spots (- (game/ras-per-epoch (count hands))
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

(defn ui-tile-bag [this {:keys [hand game auction]}]
  (when-not (::game/in-disaster? game)
    (ui/button
      (if (and my-go?
               (not auction)
               (not (game/auction-tiles-full? game)))
        {:onClick (fn []
                    (js/console.log "draw tile")
                    (comp/transact! this [(m-game/draw-tile {::hand/id (::hand/id hand)
                                                             ::game/id (::game/id game)})]))}
        {:disabled true})
      "Draw Tile")))

(defn ui-invoke-ra [this {:keys [hand game auction]}]
  (when-not (::game/in-disaster? game)
    (dom/button :.bg-red-500.text-white.font-bold.py-2.px-4.rounded.focus:outline-none.focus:shadow-outline.active:bg-red-700.focus:ring-2.focus:ring-green-500.md:hover:bg-red-700
      (if (and my-go?
               (not auction))
        {:onClick (fn []
                    (comp/transact! this [(m-game/invoke-ra {::hand/id (::hand/id hand)
                                                             ::game/id (::game/id game)})]))}
        {:disabled true
         :classes  ["opacity-50" "cursor-default"]})
      "Invoke Auction")))

(defn ui-discard-disaster-tiles [this {:keys [hand my-go? game]}]
  (filter ::tile/disaster? (::hand/tiles hand))
  (dom/div :.flex.flex-row.gap-2 {}
    (dom/div :.flex.flex-col {}
      (dom/div :.font-bold {} "Disaster! ")
      (dom/div {} "Select and discard tiles"))
    (ui/button
      (if (and my-go?
               (seq (filter ::tile/disaster? (::hand/tiles hand)))
               (hand/selected-disaster-tiles-valid? hand (filter :ui/selected? (::hand/tiles hand))))
        {:onClick (fn []
                    (comp/transact! this [(m-game/discard-disaster-tiles
                                           {::hand/id (::hand/id hand)
                                            ::game/id (::game/id game)
                                            :tile-ids (map ::tile/id (filter :ui/selected? (::hand/tiles hand)))})]))}
        {:disabled true})
      "Discard selected")))

(m/defmutation select-god-tile [{:keys [hand tile game]}]
  (action [env]
    (let [hand-ident [::hand/id (::hand/id hand)]
          tile-ident [::tile/id (::tile/id tile)]]
     (swap! (:state env)
            (fn [s]
              (-> s
                  (assoc-in (conj [::game/id (::game/id game)] :ui/selected-god-tile) tile-ident)
                  (assoc-in (conj tile-ident ::tile/hand) hand-ident)))))))

(defn ui-hands [this {:keys [hands my-player game] :as props}]
  (dom/div {}
    (->> (concat hands hands)
         (drop-while (fn [hand]
                       (not= (::player/id (::hand/player hand))
                             (::player/id my-player))))
         (take (count hands))
         (map (fn [hand]
                (ui-hand/ui-hand
                 (comp/computed hand (assoc props
                                            :click-god-tile (fn [hand tile]
                                                              (if (:ui/selected-god-tile game)
                                                                (m/set-value! this :ui/selected-god-tile nil)
                                                                (comp/transact! this [(select-god-tile {:hand hand
                                                                                                        :game game
                                                                                                        :tile tile})])))))))))))

(defn ui-bid-actions [hand {:keys [onClickSunDisk highest-bid auction]}]
  (dom/div :.flex.space-x-2.h-16.rounded-lg {}
    (concat
     (map (fn [sun-disk]
            (ui-sun-disk/ui (cond-> {:value (or sun-disk "Pass")
                                     :onClick #(onClickSunDisk sun-disk)})))
          (filter (fn [sun-disk]
                    (< (::bid/sun-disk highest-bid) sun-disk))
                  (sort (fn [a b]
                          (if (nil? a)
                            1
                            (if (nil? b)
                              -1
                              (if (< a b)
                                -1
                                (if (= a b)
                                  0
                                  1)))))
                        (concat (::hand/available-sun-disks hand)))))
     (when (auction/can-pass? auction hand)
       [(ui-sun-disk/ui-pass {:onClick #(onClickSunDisk nil)})]))))

(defn ui-action-bar [this {:keys [my-go? hand game auction] :as props}]
  (dom/div :.h-24.flex.justify-center.items-center.relative.flex-col {}
    (when (and my-go? (::hand/my-go? hand))
      (dom/div :.animate-bounce.font-bold {}
        (if auction
          "Your Bid"
          "Your turn")))
    (if (and my-go? (::hand/my-go? hand))
      (if (not (::game/auction (:game props)))
        (dom/div :.flex.flex-row.space-x-2.pt-2 {}
          (ui-tile-bag this props)
          (ui-invoke-ra this props)
          (when (::game/in-disaster? (:game props))
            (ui-discard-disaster-tiles this props)))
        (ui-bid-actions hand (assoc props
                                    :onClickSunDisk (fn [sun-disk]
                                                      (js/console.log "bid" sun-disk)
                                                      (comp/transact! this [(m-game/bid {::hand/id (::hand/id hand)
                                                                                         ::game/id (::game/id game)
                                                                                         :sun-disk sun-disk})])))))
      (dom/div :.font-bold {}
        (dom/span {} "Waiting for ")
        (dom/span {} (::player/name (::hand/player (:hand props))))))))

(defn swap-god-tile [this {:keys [game]} tile]
  (m/set-value! this :ui/selected-god-tile nil)
  (comp/transact! this [(m-game/use-god-tile {:god-tile-id (::tile/id (:ui/selected-god-tile game))
                                              ::hand/id (get-in game [:ui/selected-god-tile ::tile/hand ::hand/id])
                                              ::game/id (::game/id game)
                                              :auction-track-tile-id (::tile/id tile)})]))

(defn fill-blank-ra-spots [auction-tiles]
  (->> auction-tiles
       (count)
       (- 8)
       (range)
       (map (fn [_] (ui-tile/ui-tile {})))))

(defn ui-auction-track [this {:keys [game] :as props}]
  (dom/div :.flex.flex-row.flex-wrap.gap-2 {}
    (concat (->> (::game/auction-tiles game)
                 (sort-by ::tile/auction-track-position)
                 (map (fn [tile]
                        (dom/div
                          {:style {"animation-name"     "drawtile"
                                   "animation-duration" "1s"
                                   "transform"          "scale(1, 1)"}}
                          (ui-tile/ui-tile (comp/computed tile (cond-> {}
                                                                 (and (:ui/selected-god-tile game)
                                                                      (not (tile/god? tile))
                                                                      (not (tile/disaster? tile)))
                                                                 (assoc :on-click #(swap-god-tile this props %)
                                                                        :selectable? true))))))))
            (fill-blank-ra-spots (::game/auction-tiles game)))))

(defn ui-main-game [this {:keys [game] :as props}]
  (dom/div :.bg-gray-100 {}
    (dom/div :.flex.flex-col.md:p-2.gap-2.overflow-hidden.container.mx-auto.bg-white {}
             (menu-bar this props)
             (dom/div :.flex.flex-col.lg:flex-row {}
                      (dom/div :.flex.flex-col.lg:w-96 {}
                               (dom/div :.flex-col.w-screen.lg:w-96 {}
                                        (dom/div :.font-bold {} "Auction Count")
                                        (ui-ra-track props))
                               (dom/div :.flex-col.w-screen.lg:w-96 {}
                                        (dom/div :.font-bold {} "Tiles")
                                        (ui-auction-track this props))
                               (dom/div :.flex.items-center {}
                                        (dom/div :.font-bold {} "Current Bid Disk")
                                        (dom/div :.pl-4 {} (ui-sun-disk/ui {:value (::game/current-sun-disk game)})))
                               (dom/hr {})
                               (dom/div :.lg:order-first.pt-2 {}
                                        (ui-action-bar this props)
                                        (dom/hr :.lg:hidden {}))
                               (dom/div :.hidden.lg:block {}
                                        (dom/div :.flex-col.w-screen  {}
                                                 (dom/h3 :.font-bold "Events")
                                                 (ui-event/ui-items (reverse (::game/events game))))
                                        (dom/hr {})))
                      (dom/div :.lg:w-full {}
                               (dom/h3 :.font-bold "Seats")
                               (dom/div {}
                                 (ui-hands this props)))))))

(defmutation show-score-modal [{:keys [game-id]}]
  (action [env]
    (swap! (:state env) assoc-in [::game/id game-id :ui/show-score-modal] true)))

(defsc Auction [_ _]
  {:query [::auction/reason
           {::auction/ra-hand [::hand/id]}
           ::auction/tiles-full?
           {::auction/bids [{::bid/hand [::hand/id {::hand/player [::player/name]}]}
                            ::bid/sun-disk]}]})

(defn ui-finished [this props]
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center {}
    (dom/div :.flex.flex-col.justify-center.shadow-md.rounded.bg-gray-50.px-8.pt-6.pb-8.mb-4.items-center
      (dom/h2 :.text-lg.font-bold.pb-4 {} "Game Finished")
      (ui-score/ui-content (app/current-state this)
                           {:hand-scores (:ui/last-hand-scores props)
                            :game        props})
      (dom/div :.flex.flex-row.gap-4.pt-4 {}
        (ui/button {:onClick (fn []
                               (routing/to! this ["lobby"]))}
          "Back to lobby")))))

(defsc Game [this props]
  {:query               [{::game/players (comp/get-query ui-player/Player)}
                         {::game/events (comp/get-query ui-event/Item)}
                         ::game/current-sun-disk
                         ::game/epoch
                         {::game/auction (comp/get-query Auction)}
                         {:ui/selected-god-tile [::tile/id {::tile/hand [::hand/id]}]}
                         ::game/in-disaster?
                         {::game/last-ra-invoker [::hand/id]}
                         {::game/current-hand (comp/get-query ui-hand/Hand)}
                         {::game/ra-tiles (comp/get-query ui-tile/Tile)}
                         {::game/auction-tiles (comp/get-query ui-tile/Tile)}
                         {::game/hands (comp/get-query ui-hand/Hand)}
                         ::game/started-at
                         ::game/finished-at
                         ::game/short-id
                         :ui/show-help-modal
                         :ui/show-score-modal
                         :ui/last-hand-scores
                         {[:ui/current-player '_] (comp/get-query ui-player/Player)}
                         ::game/id]
   :ident               ::game/id
   :initial-state       {}
   :pre-merge           (fn [{:keys [data-tree current-normalized]}]
                          (let [game data-tree]
                            (merge current-normalized
                                   (cond-> data-tree
                                     (and (not= ::merge/not-found (::game/events game))
                                          (= ::event-type/finish-epoch (::event/type (last (::game/events game)))))
                                     (assoc :ui/show-score-modal true
                                            :ui/last-hand-scores (:hand-scores (::event/data (last (::game/events game)))))))))
   :componentDidMount   (fn [this]
                          (set! (.-title js/document) (str "Game " (::game/short-id (comp/props this)) (str " | Ra?"))))
   :route-segment       ["game" ::game/id]
   :will-enter          (fn [app props]
                          (let [game-id (uuid (::game/id props))
                                ident   [::game/id game-id]]
                            (dr/route-deferred ident
                                               (fn []
                                                 (df/load! app
                                                           ident
                                                           Game
                                                           {:target               [:ui/current-game]
                                                            :post-mutation        `dr/target-ready
                                                            :post-mutation-params {:target ident}})))))
   :allow-route-change? (fn [this]
                          (boolean (::game/finished-at (comp/props this))))
   :route-denied        (fn [this router relative-path]
                          (when (js/confirm "Are you sure you want to leave the game?")
                            (dr/retry-route! this router relative-path)))}
  (dom/div :.w-screen.bg-white {}
    (cond
      (::game/finished-at props)
      (ui-finished this props)
      (not (::game/started-at props))
      (ui-unstarted this props)
      :else
      (ui-main-game this
                    (let [game  props
                          p     {:game      game
                                 :my-player (:ui/current-player game)
                                 :ra-tiles  (::game/ra-tiles game)
                                 :hand      (::game/current-hand game)
                                 :hands     (::game/hands game)
                                 :auction   (::game/auction game)}]
                      (assoc p :my-go? (my-go? p)))))
    (when (:ui/show-help-modal props)
      (ui-help/ui-help-modal this))
    (when (and (not (::game/finished-at props))
               (:ui/show-score-modal props))
      (ui-score/ui-modal this {:hand-scores (:ui/last-hand-scores props)
                               :game        props
                               :close-prop  :ui/show-score-modal}))))

(def ui-game (comp/factory Game {:keyfn ::game/id}))
