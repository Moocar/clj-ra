(ns ra.model.player
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.merge :as merge]
            [ra.specs.player :as player]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [clojure.string :as str]
            [ra.specs.game :as game]))

(def local-storage-id-key "player.id")

(defn get-id-from-local-storage []
  (-> js/window .-localStorage (.getItem local-storage-id-key)))

(defn set-local-storage-id! [id]
  (-> js/window .-localStorage (.setItem local-storage-id-key id)))

(defn new-form-component []
  (comp/registry-key->class :ra.app.player/NewForm))

(defmutation new-player [input]
  (remote [_] true)
  (ok-action [env]
    (let [new-id (::player/id input)]
      (swap! (:state env)
             (fn [s]
               (-> s
                   (merge/merge-component (new-form-component) input)
                   (assoc :ui/current-player [::player/id new-id]))))
      (set-local-storage-id! new-id)
      (dr/change-route! (:app env) ["player" (str new-id)])
      (.pushState (.-history js/window) #js {} "" (str "/player/" (str new-id))))))

(defn init-new-player! [app]
  (comp/transact! app [(new-player {::player/id   (random-uuid)
                                    ::player/name ""})]))

(defn is-uuid? [s]
  (re-find #"[a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8}" s))

(defn load-game! [app game-id]
  (df/load! app
            [::game/id game-id]
            (comp/registry-key->class :ra.app.game/Game)
            {:target [:ui/current-game]
             :post-action (fn [_]
                            (dr/change-route! app ["game" (str game-id)]))}))

(defn init! [app]
  (if-let [local-storage-id (get-id-from-local-storage)]
    (let [player-id (uuid local-storage-id)
          ident     [::player/id player-id]]
      (df/load! app
                ident
                (new-form-component)
                {:target       [:ui/current-player]
                 :post-action  (fn [env]
                                 (let [player (get-in @(:state env) ident)]
                                   (if (str/blank? (::player/name player))
                                     (do
                                       (dr/change-route! (:app env) ["player" (str player-id)])
                                       (.pushState (.-history js/window) #js {} "" (str "/player/" (str player-id))))
                                     (let [path (-> js/window .-location .-pathname)]
                                       (if (str/starts-with? path "/game/")
                                         (let [game-id (second (re-find #"/game/(.*)" path))]
                                           (if (is-uuid? game-id)
                                             (load-game! app (uuid game-id))
                                             (js/console.error "game URL is not a UUID")))
                                         (do
                                           (dr/change-route! (:app env) ["lobby"])
                                           (.pushState (.-history js/window) #js {} "" "/lobby")))))))
                 :error-action (fn [env]
                                 (if-let [errors (:com.wsscode.pathom.core/errors (:body :result env))]
                                   (if (= 1 (count errors))
                                     (let [error (:error (val (first errors)))
                                           data  (:data error)]
                                       (if (= (:error data) :entity-id/missing)
                                         (init-new-player! app)
                                         (js/console.error "Unpected error" (str error))))
                                     (js/console.error "Unpected number of errors" (str errors)))
                                   (js/console.error "error action triggered, but no error" (str (:result env)))))}))
    (init-new-player! app)))

(defmutation save [input]
  (action [{:keys [state]}]
    (swap! state assoc-in [::player/id (::player/id input) ::player/name] (::player/name input)))
  (remote [_] true)
  (ok-action [env]
    (dr/change-route! (:app env) ["lobby"])
    (.pushState (.-history js/window) #js {} "" "/lobby")))
