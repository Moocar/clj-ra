(ns ra.app.lobby
  (:require [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [goog.object :as gobj]
            [ra.app.game :as ui-game]
            [ra.app.player :as ui-player]
            [ra.app.routing :as routing]
            [ra.app.ui :as ui]
            [ra.model.error :as m-error]
            [ra.model.game :as m-game]
            [ra.specs.game :as game]
            [ra.specs.player :as player]))

(defmutation set-current-game [{:keys [ident]}]
  (action [env]
    (swap! (:state env) assoc :ui/current-game ident)))

(defn click-join-game [this current-player-id short-id]
  (df/load! this [::game/short-id short-id] ui-game/Game
            {:post-action (fn [env]
                            (if-let [game-id (get-in env [:result :body [::game/short-id short-id] ::game/id])]
                              (do (merge/merge-component! this ui-game/Game (get-in env [:result :body [::game/short-id short-id]]))
                                  (comp/transact! this [(set-current-game {:ident [::game/id game-id]})
                                                        (m-game/join-game {::game/id   game-id
                                                                           ::player/id current-player-id})])
                                  (routing/to! this ["game" (str game-id)]))
                              (m-error/set-error! this "Game doesn't exist")))}))

(defsc JoinGameModal [this props]
  {:query               [:ui/join-game-code
                         {[:ui/current-player '_] [::player/id]}]
   :ident               (fn [_] [:component/id :join-game])
   :initial-state       {:ui/join-game-code ""}
   :route-segment       ["lobby" "join-game"]
   :initLocalState      (fn [this _]
                          {:save-ref (fn [r] (gobj/set this "name" r))})
   :allow-route-change? (fn [_] true)
   :will-leave          (fn [this _]
                          (m/set-value!! this :ui/join-game-code "")
                          true)
   :componentDidMount   (fn [this _ _]
                          (when-let [name-field (gobj/get this "name")]
                            (.focus name-field)))}
  (letfn [(join-game [] (click-join-game this
                                         (::player/id (:ui/current-player props))
                                         (:ui/join-game-code props)))]
    (dom/div :.h-screen.w-screen.flex.justify-center.items-center {}
      (dom/div :.flex.flex-col.justify-center.shadow-md.rounded.bg-gray-50.px-8.pt-6.pb-8.mb-4.items-center.gap-4 {}
        (dom/label :.block.text-gray-700.text-sm.font-bold.mb-2 {:htmlFor "game-code"}
          "Enter Game Code")
        (dom/div {}
          (ui/input props :ui/join-game-code
            {:id          "game-code"
             :type        "text"
             :placeholder "E.g AXKQ"
             :ref         (comp/get-state this :save-ref)
             :onKeyUp     (fn [evt]
                            (when (= (.-keyCode evt) 13)
                              (join-game)))
             :onChange    (fn [evt _]
                            (m/set-string!! this :ui/join-game-code :event evt))}))
        (ui/button {:onClick (fn [] (join-game))}
          "Load Game")
        (ui/button {:onClick (fn []
                               ;; Ugly
                               (routing/back! this ["lobby"]))}
          "Back")))))

(def ui-join-game-modal (comp/factory JoinGameModal))

(defsc Lobby [this props]
  {:query             [{[:ui/current-player '_] (comp/get-query ui-player/NewForm)}]
   :ident             (fn [_] [:component/id :lobby])
   :route-segment     ["lobby"]
   :initial-state     {}
   :componentDidMount (fn [_] (set! (.-title js/document) "Lobby | Ra?"))}
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center {}
    (dom/div :.flex.flex-col.justify-center.shadow-md.rounded.bg-gray-50.px-8.pt-6.pb-8.mb-4.items-center.gap-8 {}
      (dom/div :.block.text-gray-700.text-sm.font-bold.mb-2 {}
        (dom/span {} "Welcome ")
        (dom/span {} (::player/name (:ui/current-player props))))
      (ui/button {:onClick (fn []
                             (comp/transact! this [(m-game/new-game {})]))}
        "Create New Game")
      (ui/button {:onClick (fn []
                             (routing/to! this ["lobby" "join-game"]))}
        "Join Game"))))

(def ui-lobby (comp/factory Lobby))
