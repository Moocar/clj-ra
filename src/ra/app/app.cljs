(ns ra.app.app
  (:require [com.fulcrologic.fulcro.networking.http-remote :as http]
            [com.fulcrologic.fulcro.networking.websockets :as fws]
            [com.fulcrologic.fulcro.application :as app]))

(defonce APP
  (app/fulcro-app
   {:remotes {;; :remote (http/fulcro-http-remote {:url "/api"})
              :remote (fws/fulcro-websocket-remote {:push-handler (fn [{:keys [topic msg] :as all}]
                                                                    (js/console.log "received" all))})
              }}))
