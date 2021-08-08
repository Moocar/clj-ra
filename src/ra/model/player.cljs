(ns ra.model.player
  (:require [clojure.string :as str]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [ra.app.routing :as routing]
            [ra.specs.player :as player]))

(def local-storage-id-key "player.id")

(defn get-id-from-local-storage []
  (-> js/window .-localStorage (.getItem local-storage-id-key)))

(defn set-local-storage-id! [id]
  (-> js/window .-localStorage (.setItem local-storage-id-key id)))

(defn new-form-component []
  (comp/registry-key->class :ra.app.player/NewForm))

(defmutation new-player [input]
  (remote [env]
    (-> env
        (m/returning (new-form-component))
        (m/with-target [:ui/current-player])))
  (ok-action [env]
    (let [new-id (::player/id input)]
      (set-local-storage-id! new-id)
      (routing/to! (:app env) ["player" (str new-id)]))))

(defn init-new-player! [app]
  (let [id (random-uuid)]
    (comp/transact! app [(new-player {::player/id   id
                                      ::player/name ""})])))

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
                                     (routing/to! (:app env) ["player" (str player-id)])
                                     (routing/handle-window! app))))
                 :error-action (fn [env]
                                 (if-let [errors (:com.wsscode.pathom.core/errors (:body (:result env)))]
                                   (if (= 1 (count errors))
                                     (let [error (:error (val (first errors)))]
                                       (if (= (:msg error) "Player not found")
                                         (init-new-player! app)
                                         (js/console.error "Unexpected error" (str error))))
                                     (js/console.error "Unexpected number of errors" (str errors)))
                                   (js/console.error "error action triggered, but no error" (str (:result env)))))}))
    (init-new-player! app)))

(defmutation save [input]
  (remote [env]
    (m/returning env (new-form-component)))
  (ok-action [env]
    (routing/to! (:app env) ["lobby"])))
