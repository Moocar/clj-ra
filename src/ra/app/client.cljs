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
            [ra.specs.hand :as hand]
            [ra.specs.auction.bid :as bid]
            [ra.specs.auction :as auction]
            [ra.model.game :as m-game]
            [ra.specs.auction.reason :as auction-reason]))

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

(defn start-server-event-listen! [app]
  (async/go
    (loop []
      (let [{:keys [game new-events events-included?]} (async/<! client-app/loader-ch)]
        (assert (not (and (seq new-events) events-included?)))
        (merge/merge-component! app
                                (comp/registry-key->class :ra.app.game/GameWithoutEvents)
                                game
                                :remove-missing? true)
        (when events-included?
          (merge/merge-component! app
                                  (comp/registry-key->class :ra.app.game/Game)
                                  (select-keys game [::game/id ::game/events])))
        (doseq [event new-events]
          (merge/merge-component! app
                                  (comp/registry-key->class :ra.app.event/Item)
                                  event
                                  :append [::game/id (::game/id game) ::game/events]))
        (let [state-map (app/current-state app)
              hand      (::game/current-hand game)
              my-go?    (= (:ui/current-player state-map)
                           [::player/id (::player/id (::hand/player hand))])
              auction   (::game/auction game)]
          (if (and auction (empty? (::auction/bids auction)) (= ::auction-reason/draw (::auction/reason auction)))
            (audio/play-random-ra! state-map)
            (when my-go?
              (audio/play-ding! state-map)
              (let [highest-bid (auction/highest-bid auction)
                    can-bid?    (empty? (filter (fn [sun-disk]
                                                  (< (::bid/sun-disk highest-bid) sun-disk))
                                                (::hand/available-sun-disks hand)))]
                (when (and auction can-bid?)
                  (comp/transact! app [(m-game/bid {::hand/id (::hand/id hand)
                                                    ::game/id (::game/id game)
                                                    :sun-disk nil})]))))))
        (recur)))))

(defn ^:export start []
  (let [app client-app/APP]
    (app/set-root! app Root {:initialize-state? true})
    (dr/change-route! app ["home"])
    (set! (.-onpopstate js/window)
          (fn [_] (routing/handle-window! app)))
    (app/mount! app Root "app" {:initialize-state? false})
    (start-server-event-listen! app)
    (m-player/init! app)
    (audio/load-and-store-ding! app)
    (audio/load-and-store-ras! app)
    ))
