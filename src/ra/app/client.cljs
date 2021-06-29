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
            [ra.app.ui :as ui]))

(defsc PlayerDetails [this {:keys [::player/id ::player/temp-name] :as props}]
  {:query         [::player/id ::player/temp-name ::player/name]
   :initial-state {::player/temp-name ""}
   :ident         ::player/id}
  (dom/div :.w-full.max-w-xs {}
    (dom/div :.bg-white.shadow-md.rounded.px-8.pt-6.pb-8.mb-4 {}
      (dom/div :.mb-4 {}
        (dom/label :.block.text-gray-700.text-sm.font-bold.mb-2 {:for "username"}
          "Username")
        (ui/input props ::player/temp-name
          {:id          "username"
           :type        "text"
           :placeholder "Username"
           :onKeyUp     (fn [evt]
                          (when (= (.-keyCode evt) 13)
                            (comp/transact! this [(m-player/save
                                                   {::player/id   id
                                                    ::player/name temp-name})]
                                            {:refresh [:ui/current-player]})))
           :onChange    (fn [evt _]
                          (m/set-string! this ::player/temp-name :event evt))}))
      (dom/div :.flex.items-center.justify-between {}
        (ui/button {:onClick (fn []
                               (comp/transact! this [(m-player/save
                                                      {::player/id   id
                                                       ::player/name temp-name})]
                                               {:refresh [:ui/current-player]}))}
            "Submit")))))

(def ui-player-details (comp/factory PlayerDetails {:keyfn ::player/id}))

(defsc Root [this {:keys [:ui/current-player :ui/current-game :ui/error-occurred]}]
  {:query         [{[:ui/current-player '_] (comp/get-query PlayerDetails)}
                   {[:ui/current-game '_] (comp/get-query ui-game/Game)}
           :ui/error-occurred]
   :initial-state {}}
  (dom/div :.container.mx-auto.flex.justify-center {}
    (if (nil? current-player)
      (dom/p "loading")
      (if (str/blank? (::player/name current-player))
        (ui-player-details current-player)
        (dom/div {}
          (dom/p (str "hi there " (::player/name current-player)))
          (if (nil? current-game)
            (dom/button {:primary true
                         :onClick (fn []
                                    (comp/transact! this [(m-game/new-game {})]))}
                        "New Game")
            (ui-game/ui-game (merge current-game {:ui/current-player current-player}))))))
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
