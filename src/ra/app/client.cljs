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
            [com.fulcrologic.semantic-ui.views.card.ui-card :refer [ui-card]]
            [com.fulcrologic.semantic-ui.views.card.ui-card-group :refer [ui-card-group]]
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
            [ra.app.game :as ui-game]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.model.player :as m-player]
            [clojure.string :as str]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [ra.specs.hand :as hand]))

(defsc PlayerDetails [this {:keys [::player/id ::player/temp-name] :as input}]
  {:query         [::player/id ::player/temp-name ::player/name]
   :initial-state {::player/temp-name ""}
   :ident         ::player/id}
  (dom/div {}
    (ui-input {:label    "Your Name"
               :value    (or temp-name "")
               :onChange (fn [evt _]
                           (m/set-string! this ::player/temp-name :event evt))})
    (ui-button {:content  "Submit"
                :primary true
                :onClick  (fn []
                            (comp/transact! this [(m-player/save
                                                   {::player/id id
                                                    ::player/name temp-name})]
                                            {:refresh [:ui/current-player]}))})))

(def ui-player-details (comp/factory PlayerDetails {:keyfn ::player/id}))

(defsc Root [this {:keys [:ui/current-player :ui/current-game :ui/error-occurred]}]
  {:query [{[:ui/current-player '_] (comp/get-query PlayerDetails)}
           {[:ui/current-game '_] (comp/get-query ui-game/Game)}
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
            (ui-game/ui-game (merge current-game {:ui/current-player current-player}))))))
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
