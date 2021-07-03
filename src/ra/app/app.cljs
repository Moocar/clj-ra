(ns ra.app.app
  (:require [com.fulcrologic.fulcro.networking.http-remote :as http]
            [com.fulcrologic.fulcro.networking.websockets :as fws]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.components :as comp]
            [ra.specs.game :as game]
            [com.fulcrologic.fulcro.data-fetch :as df]))

(defmutation hide-error [_]
  (action [{:keys [state app]}]
          (swap! state assoc :ui/error-occurred false)))

(defmutation show-error [_]
  (action [{:keys [state app]}]
          (swap! state assoc :ui/error-occurred true)
          (js/setTimeout #(comp/transact! app [(hide-error {})]) 3000)))

(defonce APP
  (app/fulcro-app
   {:remotes {;; :remote (http/fulcro-http-remote {:url "/api"})
              :remote (fws/fulcro-websocket-remote
                       {:push-handler (fn [{:keys [topic msg] :as all}]
                                        (let [{:keys [::game/id]} msg]
                                          (df/load! APP [::game/id id] (comp/registry-key->class :ra.app.game/Game))))
                        :global-error-callback #(comp/transact! APP [(show-error {})] )})}}))
