(ns ra.app.client
  (:require [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.routing.dynamic-routing
             :as
             dr
             :refer
             [defrouter]]
            [ra.app.app :as client-app]
            [ra.app.game :as ui-game]
            [ra.app.lobby :as ui-lobby]
            [ra.app.player :as ui-player]
            [ra.model.player :as m-player]
            [ra.specs.player :as player]))

(defsc Home [_ _]
  {:query []
   :ident (fn [_] [:component/id :home])
   :initial-state {}
   :route-segment ["home"]}
  (dom/div ""))

(defrouter RootRouter [this props]
  {:router-targets [Home
                    ui-player/NewForm
                    ui-lobby/Lobby
                    ui-game/Game]
   :initial-state {}}
  (case (:current-state props)
    :pending (dom/div "Loading...")
    :failed (dom/div "Failed!")
    (dom/div "")))

(def ui-root-router (comp/factory RootRouter))

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



(defsc Root [this props]
  {:query         [{:ui/router (comp/get-query RootRouter)}
                   [:ui/global-error '_]]
   :initial-state {:ui/router {}}}
  (dom/div :.relative {}
    (ui-root-router (:ui/router props))
    (ui-error this (:ui/global-error props))))

(defn ^:export refresh []
  (app/mount! client-app/APP Root "app"))

(defn ^:export start []
  (let [app client-app/APP]
    (app/set-root! app Root {:initialize-state? true})
    (dr/change-route! app ["home"])
    (app/mount! app Root "app" {:initialize-state? false})
    (m-player/init! app)))
