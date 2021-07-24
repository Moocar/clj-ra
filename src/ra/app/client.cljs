(ns ra.app.client
  (:require [clojure.core.async :as async]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.routing.dynamic-routing
             :as
             dr
             :refer
             [defrouter]]
            [ra.app.app :as client-app]
            [ra.app.audio :as audio]
            [ra.app.error :as ui-error]
            [ra.app.game :as ui-game]
            [ra.app.lobby :as ui-lobby]
            [ra.app.player :as ui-player]
            [ra.app.routing :as routing]
            [ra.model.player :as m-player]
            [ra.specs.player :as player]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]))

(defsc Home [_ _]
  {:query []
   :ident (fn [_] [:component/id :home])
   :initial-state {}
   :route-segment ["home"]}
  (dom/div ""))

(defrouter RootRouter [_ props]
  {:router-targets [Home
                    ui-player/NewForm
                    ui-lobby/Lobby
                    ui-lobby/JoinGameModal
                    ui-game/Game]
   :initial-state {}}
  (case (:current-state props)
    :pending (dom/div "Loading...")
    :failed (dom/div "Failed!")
    (dom/div "")))

(def ui-root-router (comp/factory RootRouter))

(defsc Root [_ props]
  {:query         [{:ui/router (comp/get-query RootRouter)}
                   {:>/global-error (comp/get-query ui-error/Error)}]
   :initial-state {:ui/router {}
                   :>/global-error {}}}
  (dom/div :.relative.bg-gray-50 {}
    (ui-root-router (:ui/router props))
    (ui-error/ui-modal (:>/global-error props))))

(defn ^:export refresh []
  (app/mount! client-app/APP Root "app"))

(defn ^:export start []
  (let [app client-app/APP]
    (app/set-root! app Root {:initialize-state? true})
    (dr/change-route! app ["home"])
    (set! (.-onpopstate js/window)
          (fn [_] (routing/handle-window! app)))
    (async/go
      (loop []
        (let [game (async/<! client-app/loader-ch)]
          (merge/merge-component! app
                                  (comp/registry-key->class :ra.app.game/Game)
                                  game
                                  :remove-missing? true)
          (let [state-map (app/current-state app)]
            (when (= (::player/id (::hand/player (::game/current-hand game)))
                     (second (get state-map :ui/current-player)))
              (audio/play-ding! state-map)))
          (async/<! (async/timeout 1))
          (recur))))
    (app/mount! app Root "app" {:initialize-state? false})
    (m-player/init! app)
    (audio/load-and-store-ding! app)))
