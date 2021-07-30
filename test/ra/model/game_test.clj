(ns ra.model.game-test
  (:require [clojure.test :as t]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [integrant.core :as ig]
            [ra.db :as db]
            [ra.instrument :as ins]
            [ra.model.game :as m-game]
            [ra.model.player :as m-player]
            [ra.pathom :as pathom]
            [ra.specs.game :as game]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [datascript.core :as d])
  (:import (clojure.lang ExceptionInfo)))

(defn new-game [p]
  (let [r (p {} [`(m-game/new-game {})])]
    (get-in r [`m-game/new-game ::game/id])))

(defn make-serial-parser [{:keys [resolvers extra-env]}]
  (try
    (p/parser
     {::p/env     {::p/reader                 [p/map-reader
                                               pc/reader2
                                               pc/open-ident-reader
                                               p/env-placeholder-reader
                                               ]
                   ::p/placeholder-prefixes   #{">"}
                   ::pc/mutation-join-globals [:tempids]
                   }
      ::p/mutate  pc/mutate
      ::p/plugins [(pc/connect-plugin {::pc/register resolvers})
                   (p/env-wrap-plugin #(merge % extra-env))
                   (p/post-process-parser-plugin p/elide-not-found)]})
    (catch Exception e
      (println e)
      (throw e))))

(defmethod ig/init-key ::parser [_ extra-env]
  (make-serial-parser {:resolvers (pathom/make-resolvers)
                       :extra-env extra-env}))

(defn start-env []
  (let [system-config {:ra.db/conn             {}
                       :ra.model.game/ref-data {:ra.db/conn (ig/ref :ra.db/conn)}
                       ::parser                {:ra.db/conn (ig/ref :ra.db/conn)
                                                :ref-data   (ig/ref :ra.model.game/ref-data)}}
        _             (ig/load-namespaces system-config)
        env           (ig/init system-config)]
    (assoc env ::pathom/parser (::parser env))))

(t/deftest need-2-players-to-start
  (let [{:keys [::parser]} (start-env)
        p1-id              (db/uuid)]
    (parser {} [`(m-player/new-player {::player/id ~p1-id})])
    (let [game-id (new-game parser)]
      (parser {} [`(m-game/join-game {::game/id ~game-id ::player/id ~p1-id})])
      (t/is (thrown-with-msg? ExceptionInfo
                              #"Need at least two players"
                              (parser {} [`(m-game/start-game {::game/id ~game-id})]))))))

(t/deftest max-5-players
  (let [{:keys [::parser]} (start-env)
        player-ids (repeatedly 6 db/uuid)]
    (doseq [player-id player-ids]
      (parser {} [`(m-player/new-player {::player/id ~player-id})]))
    (let [game-id (new-game parser)]
      (doseq [player-id (butlast player-ids)]
        (parser {} [`(m-game/join-game {::game/id ~game-id ::player/id ~player-id})]))
      (t/is (thrown-with-msg? ExceptionInfo
                              #"Maximum players already reached"
                              (parser {} [`(m-game/join-game {::game/id ~game-id ::player/id ~(last player-ids)})]))))))

(t/deftest check-use-god-tile-in-bid
  (let [{:keys [::parser ::db/conn] :as env} (start-env)
        player-ids (repeatedly 2 db/uuid)]
    (doseq [player-id player-ids]
      (parser {} [`(m-player/new-player {::player/id ~player-id})]))
    (let [game-id (new-game parser)]
      (doseq [player-id player-ids]
        (parser {} [`(m-game/join-game {::game/id ~game-id ::player/id ~player-id})]))
      (parser {} [`(m-game/start-game {::game/id ~game-id})])
      (let [game     (d/touch (d/entity @conn [::game/id game-id]))
            playbook [[0 :draw {:tile tile/god?}]
                      [1 :invoke-ra {}]
                      [0 :bid {:sun-disk :rand}]
                      [1 :bid {:sun-disk :pass}]
                      [0 :draw {:tile :safe}]
                      [1 :invoke-ra {}]
                      [0 :use-god-tile {:tile :safe}]]]
        (t/is (thrown-with-msg? ExceptionInfo
                                #"Can't use god tile during auction"
                                (ins/run-playbook env game playbook)))))))
