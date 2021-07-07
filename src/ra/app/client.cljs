(ns ra.app.client
  (:require [clojure.string :as str]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.app.app :as client-app]
            [ra.app.game :as ui-game]
            [ra.model.game :as m-game]
            [ra.model.player :as m-player]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [ra.app.ui :as ui]
            [ra.app.player :as ui-player]
            [com.fulcrologic.fulcro.mutations :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]))

(defn left-menu [props]
  (dom/p :.mb-4.text-gray-700.text-sm.font-bold.absolute.top-0.left-0 {}
    "Menu"))

(defn right-menu [props]
  (dom/p :.mb-4.text-gray-700.text-sm.font-bold.absolute.top-0.right-0 {}
    (str "User: " (::player/name (:ui/current-player props)))))

(defn top-menu [props]
  (dom/div :.relative.h-6.border-b-2.mb-2 {}
    (left-menu props)
    (right-menu props)))

(defn new-game-modal [this props]
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center {}
    (dom/div :.flex.flex-col.justify-center.shadow-md.rounded.bg-gray-50.px-8.pt-6.pb-8.mb-4.items-center.gap-8 {}
      (dom/div :.block.text-gray-700.text-sm.font-bold.mb-2 {}
        (dom/span {} "Welcome ")
        (dom/span {} (::player/name (:ui/current-player props))))
      (ui/button {:onClick (fn []
                             (comp/transact! this [(m-game/new-game {})]))}
        "Create New Game")
      (ui/button {:onClick (fn []
                             (m/set-value! this :ui/join-game true))}
        "Join Game"))))

(defmutation clear-join-game [{}]
  (action [env]
    (swap! (:state env)
           (fn [s]
             (-> s
                 (update-in [:component/id :lobby] assoc :ui/join-game nil :ui/join-game-code nil))))))

(defmutation set-current-game [{:keys [ident]}]
  (action [env]
    (swap! (:state env) assoc :ui/current-game ident)))

(defmutation set-error [{:keys [msg]}]
  (action [env]
    (swap! (:state env) assoc :ui/global-error msg)))

(defmutation clear-error [{}]
  (action [env]
    (swap! (:state env) assoc :ui/global-error nil)))

(defn click-join-game [this short-id]
  (df/load! this [::game/short-id short-id] ui-game/Game
            {:post-action (fn [env]
                            (if-let [game-id (get-in env [:result :body [::game/short-id short-id] ::game/id])]
                              (do (merge/merge-component! this ui-game/Game (get-in env [:result :body [::game/short-id short-id]]))
                                  (comp/transact! this [(set-current-game {:ident [::game/id game-id]}) (clear-join-game {})]))
                              (comp/transact! this [(set-error {:msg "Game doesn't exist"})])))}))

(defn join-game-modal [this props]
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center {}
    (dom/div :.flex.flex-col.justify-center.shadow-md.rounded.bg-gray-50.px-8.pt-6.pb-8.mb-4.items-center.gap-4 {}
      (dom/label :.block.text-gray-700.text-sm.font-bold.mb-2 {:htmlFor "game-code"}
        "Enter Game Code")
      (dom/div {}
        (ui/input props :ui/join-game-code
          {:id          "game-code"
           :type        "text"
           :placeholder "E.g AXKQ"
           :onKeyUp     (fn [evt]
                          (when (= (.-keyCode evt) 13)
                            (click-join-game this (:ui/join-game-code props))))
           :onChange    (fn [evt _]
                          (m/set-string! this :ui/join-game-code :event evt))}))
      (ui/button {:onClick (fn []
                             (click-join-game this (:ui/join-game-code props)))}
        "Load Game")
      (ui/button {:onClick (fn []
                             (comp/transact! this [(clear-join-game {})]))}
        "Back"))))

(defn ui-error [this err]
  (when err
    (dom/div :.h-screen.w-screen.flex.justify-center.items-center.absolute.z-50.top-0 {}
      (dom/div :.relative {}
       (dom/div :.font-bold.right-2.top-1.absolute.text-sm
         {:onClick (fn [] (comp/transact! this [(clear-error {})]))}
         "X")
        (dom/div :.flex.flex-col.shadow-md.rounded.bg-red-200.px-8.pt-6.pb-8.mb-4.items-center {}
          (dom/h1 :.font-bold {} "Error")
          (dom/p err))))))

(defsc Lobby [this props]
  {:query         [{[:ui/current-game '_] (comp/get-query ui-game/Game)}
                   {[:ui/current-player '_] (comp/get-query ui-player/NewForm)}
                   :ui/join-game
                   :ui/join-game-code]
   :ident         (fn [_] [:component/id :lobby])
   :initial-state {}}
  (if (:ui/join-game props)
    (join-game-modal this props)
    (if (nil? (:ui/current-game props))
      (new-game-modal this props)
      (ui-game/ui-game (merge (:ui/current-game props) {:ui/current-player (:ui/current-player props)})))))

(def ui-lobby (comp/factory Lobby))

(defsc Root [this {:keys [:ui/current-player] :as props}]
  {:query         [{[:ui/current-player '_] (comp/get-query ui-player/NewForm)}
                   {:ui/lobby (comp/get-query Lobby)}
                   [:ui/global-error '_]]
   :initial-state {:ui/lobby {}}}
  (dom/div :.relative {}
    (if (nil? current-player)
      ;; loading
      (dom/p "")
      ;; ask user for their name
      (if (str/blank? (::player/name current-player))
        (ui-player/ui-new-form current-player)

        ;; Else, take them to the lobby
        (ui-lobby (:ui/lobby props))))
    (ui-error this (:ui/global-error props))))

(defn init-player-local-storage []
  (if-let [player-id (-> js/window .-localStorage (.getItem "player.id"))]
    (comp/transact! client-app/APP [(m-player/use-local-storage-player {:player-id (uuid player-id)})])
    (comp/transact! client-app/APP [(m-player/init-local-storage {})])))

(defn is-uuid? [s]
  (re-find #"[a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8}" s))

(defn init-game-from-url []
  (let [path (-> js/window .-location .-pathname)]
    (when (str/starts-with? path "/game/")
      (let [game-id (second (re-find #"/game/(.*)" path))]
        (when (is-uuid? game-id)
          (df/load! client-app/APP [::game/id (uuid game-id)] ui-game/Game
                    {:target [:ui/current-game]}))))))

(defn ^:export refresh []
  (js/console.log "refresh")
  (app/mount! client-app/APP Root "app"))

(defn ^:export start []
  (js/console.log "start")
  (js/console.log "mounting")
  (app/mount! client-app/APP Root "app")
  (init-player-local-storage)
  (init-game-from-url))
