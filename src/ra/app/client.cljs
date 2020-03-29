(ns ra.app.client
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.semantic-ui.elements.button.ui-button :refer [ui-button]]
            [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
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
            [ra.specs.user :as user]
            [ra.model.game :as m-game]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.mutations :as m]
            [ra.model.user :as m-user]
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

(defsc Epoch [_ _]
  {:query [::epoch/current-sun-disk
           ::epoch/number
           {::epoch/ra-tiles (comp/get-query Tile)}
           {::epoch/auction-tiles (comp/get-query Tile)}
           {::epoch/last-ra-invokee (comp/get-query Player)}
           {::epoch/player-hands (comp/get-query PlayerHand)}]})

(defsc Game [_ _]
  {:query [{::game/players (comp/get-query Player)}
           {::game/current-epoch (comp/get-query Epoch)}
;;           {::game/tile-bag (comp/get-query Tile)}
           ::game/id]
   :ident (fn [] [:component/id :ra.app.game])})

(defsc UserDetails [this {:keys [::user/name] :as input}]
  {:query [::user/id ::user/name]
   :initial-state {::user/name ""}
   :ident ::user/id}
  (dom/div {}
    (ui-input {:label    "Your Name"
               :value    name
               :onChange (fn [evt _]
                           (m/set-string! this ::user/name :event evt))})
    (ui-button {:content  "Submit"
                :primary true
                :onClick  (fn []
                            (comp/transact! this [(m-user/save input)]))})))

(def ui-user-details (comp/factory UserDetails {:keyfn ::user/id}))

(defsc Root [this {:keys [::app/active-remotes :current-user]}]
  {:query [[::app/active-remotes '_]
           {:current-user (comp/get-query UserDetails)}]
   :initial-state {}}
  (if (nil? current-user)
    (dom/p "loading")
    (if (str/blank? (::user/name current-user))
      (ui-user-details current-user)
      (dom/div {}
        (when (seq active-remotes)
          (dom/div :.ui.active.inline.loader))
        (ui-button {:primary true
                    :onClick (fn []
                               (comp/transact! this [(m-game/new-game {})]))}
                   "New Gamess")
        (dom/p (str "hi there " (::user/name current-user)))))))

(defn ^:export refresh []
  (js/console.log "refresh")
  (app/mount! client-app/APP Root "app"))

(defn ^:export start []
  (js/console.log "start")
  (js/console.log "mounting")
  (app/mount! client-app/APP Root "app")
  (if-let [user-id (-> js/window .-localStorage (.getItem "user.id"))]
    (comp/transact! client-app/APP [(m-user/use-local-storage-user {:user-id (uuid user-id)})])
    (comp/transact! client-app/APP [(m-user/init-local-storage {})]))
  (let [url (-> js/window .-location .-href)]
    (js/console.log "url" url))
  (dr/initialize! client-app/APP))
