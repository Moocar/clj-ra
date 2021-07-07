(ns ra.app.client
  (:require [clojure.string :as str]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [ra.app.app :as client-app]
            [ra.app.game :as ui-game]
            [ra.app.lobby :as ui-lobby]
            [ra.app.player :as ui-player]
            [ra.app.routing :as routing]
            [ra.model.player :as m-player]
            [ra.specs.game :as game]
            [ra.specs.player :as player]))

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

(defmutation set-error [{:keys [msg]}]
  (action [env]
    (swap! (:state env) assoc :ui/global-error msg)))

(defmutation clear-error [{}]
  (action [env]
    (swap! (:state env) assoc :ui/global-error nil)))

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

(defsc Root [this {:keys [:ui/current-player] :as props}]
  {:query         [{[:ui/current-player '_] (comp/get-query ui-player/NewForm)}
                   {:ui/lobby (comp/get-query ui-lobby/Lobby)}
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
        (ui-lobby/ui-lobby (:ui/lobby props))))
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
  (routing/start!)
  (init-player-local-storage)
  (init-game-from-url))
