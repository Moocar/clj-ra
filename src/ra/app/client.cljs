(ns ra.app.client
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.semantic-ui.elements.button.ui-button :refer [ui-button]]
            [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
            [com.fulcrologic.semantic-ui.elements.label.ui-label :refer [ui-label]]
            [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]
            [com.fulcrologic.semantic-ui.collections.form.ui-form-field :refer [ui-form-field]]
            [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
            [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]
            [com.fulcrologic.semantic-ui.modules.modal.ui-modal-header :refer [ui-modal-header]]
            [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
            [ra.app.app :as client-app]
            [ra.specs.tile :as tile]
            [ra.specs.player :as player]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [ra.model.game :as m-game]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.model.player :as m-player]
            [clojure.string :as str]
            [com.fulcrologic.fulcro.data-fetch :as df]))

(defsc Tile [_ _]
  {:query [::tile/id
           ::tile/title
           ::tile/disaster?
           ::tile/type]})

(defsc PlayerHand [_ _]
  {:query [{::player/tiles (comp/get-query Tile)}
           ::player/sun-disks
           ::player/id]})

(defsc Player [_ _]
  {:query [::player/name
           ::player/id
           ::player/score]})

(defsc Epoch [_ {:keys [::epoch/number ::epoch/current-sun-disk]}]
  {:query [::epoch/current-sun-disk
           ::epoch/number
           {::epoch/ra-tiles (comp/get-query Tile)}
           {::epoch/auction-tiles (comp/get-query Tile)}
           {::epoch/last-ra-invokee (comp/get-query Player)}
           {::epoch/player-hands (comp/get-query PlayerHand)}]}
  (dom/div {}
    (dom/p (str "Epoch: " number))
    (dom/p (str "Middle Sun Disk: " current-sun-disk))
    ))

(def ui-epoch (comp/factory Epoch {:keyfn ::epoch/number}))

(defsc Game [this {:keys [::game/players
                          ::game/current-epoch
                          ::game/started-at
                          ::game/id
                          current-player] :as props}]
  {:query [{::game/players (comp/get-query Player)}
           {::game/current-epoch (comp/get-query Epoch)}
           ;;           {::game/tile-bag (comp/get-query Tile)}
           ::game/started-at
           {[:current-player '_] (comp/get-query Player)}
           ::game/id]
   :ident ::game/id}
  (dom/div {}
    (if (first (filter #(= (::player/id %) (::player/id current-player))
                       players))
      (dom/div {}
        (dom/p "Game players")
        (dom/ul
         (map #(dom/li (::player/name %)) players))
        (if started-at
          (dom/div {}
            (dom/p "game started")
            (ui-epoch current-epoch))
          (ui-button {:primary true
                      :onClick (fn []
                                 (comp/transact! this [(m-game/start-game (select-keys props [::game/id]))]))}
                     "Start Game")))
      (ui-button {:primary true
                  :onClick (fn []
                             (comp/transact! this [(m-game/join-game {::game/id id ::player/id (::player/id current-player)})]))}
                 "Join Game"))))

(def ui-game (comp/factory Game {:keyfn ::game/id}))

(defsc PlayerDetails [this {:keys [::player/name] :as input}]
  {:query         [::player/id ::player/name]
   :initial-state {::player/name ""}
   :ident         ::player/id}
  (js/console.log "input" input)
  (dom/div {}
    (ui-input {:label    "Your Name"
               :value    (or name "")
               :onChange (fn [evt _]
                           (m/set-string! this ::player/name :event evt))})
    (ui-button {:content  "Submit"
                :primary true
                :onClick  (fn []
                            (comp/transact! this [(m-player/save input)] {:refresh [:current-player]}))})))

(def ui-player-details (comp/factory PlayerDetails {:keyfn ::player/id}))

(defsc Root [this {:keys [:current-player :current-game :ui/error-occurred]}]
  {:query [{[:current-player '_] (comp/get-query PlayerDetails)}
           {[:current-game '_] (comp/get-query Game)}
           :ui/error-occurred]
   :initial-state {}}
  (dom/div {}
    (if (nil? current-player)
      (dom/p "loadings")
      (if (str/blank? (::player/name current-player))
        (ui-player-details current-player)
        (dom/div {}
          (dom/p (str "hi there " (::player/name current-player)))
          (if (nil? current-game)
            (ui-button {:primary true
                        :onClick (fn []
                                   (comp/transact! this [(m-game/new-game {})]))}
                       "New Game")
            (ui-game (merge current-game {:current-player current-player}))))))
    (when error-occurred
      (ui-label {:color "red"} "ERROR!"))))

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
          (df/load! client-app/APP [::game/id (uuid game-id)] Game
                    {:target [:current-game]}))))))

(defn ^:export refresh []
  (js/console.log "refresh")
  (app/mount! client-app/APP Root "app"))

(defn ^:export start []
  (js/console.log "start")
  (js/console.log "mounting")
  (app/mount! client-app/APP Root "app")
  (init-player-local-storage)
  (init-game-from-url))
