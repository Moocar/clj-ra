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
            [ra.app.player :as ui-player]))

(defn left-menu [props]
  (dom/p :.mb-4.text-gray-700.text-sm.font-bold.absolute.top-0.left-0 {} "Menu"))

(defn right-menu [props]
  (dom/p :.mb-4.text-gray-700.text-sm.font-bold.absolute.top-0.right-0 {} (str "User: " (::player/name (:ui/current-player props)))))

(defn top-menu [props]
  (dom/div :.relative.h-6.border-b-2.mb-2 {}
           (left-menu props)
           (right-menu props)))

(defn ui-lobby [this props]
  (dom/div :.w-full.max-w-5xl.relative {}
           (dom/div :.bg-white.shadow-md.rounded.px-8.pt-6.pb-8.mb-4 {}
                    (top-menu props)
                    (if (nil? (:ui/current-game props))
                      (ui/button {:onClick (fn []
                                             (comp/transact! this [(m-game/new-game {})]))}
                        "New Game")
                      (ui-game/ui-game (merge (:ui/current-game props) {:ui/current-player (:ui/current-player props)}))))))

(defsc Root [this {:keys [:ui/current-player :ui/error-occurred] :as props}]
  {:query         [{[:ui/current-player '_] (comp/get-query ui-player/NewForm)}
                   {[:ui/current-game '_] (comp/get-query ui-game/Game)}
                   :ui/error-occurred]
   :initial-state {}}
  (dom/div :.container.mx-auto.flex.justify-center {}
    (if (nil? current-player)
      (dom/p "loading")
      (if (str/blank? (::player/name current-player))
        (ui-player/ui-new-form current-player)
        (ui-lobby this props)))
    (when error-occurred
      (dom/label {:color "red"} "ERROR!"))))

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
