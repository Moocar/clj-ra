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
            [datascript.core :as d]
            [ra.specs.tile.type :as tile-type]
            [ra.specs.hand :as hand])
  (:import (clojure.lang ExceptionInfo)))

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

(defn new-game [p]
  (let [r (p {} [`(m-game/new-game {})])]
    (get-in r [`m-game/new-game ::game/id])))

(defn setup-game [{:keys [::parser ::db/conn]} player-count]
  (let [player-ids (repeatedly player-count db/uuid)]
    (doseq [player-id player-ids]
      (parser {} [`(m-player/new-player {::player/id ~player-id})]))
    (let [game-id (new-game parser)]
      (doseq [player-id player-ids]
        (parser {} [`(m-game/join-game {::game/id ~game-id ::player/id ~player-id})]))
      (parser {} [`(m-game/start-game {::game/id ~game-id})])
      (d/entity @conn [::game/id game-id]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  (let [env      (start-env)
        game     (setup-game env 2)
        playbook [[0 :draw {:tile tile/god?}]
                  [1 :invoke-ra {}]
                  [0 :bid {:sun-disk :rand}]
                  [1 :bid {:sun-disk :pass}]
                  [0 :draw {:tile :safe}]
                  [1 :invoke-ra {}]
                  [0 :use-god-tile {:tile :safe}]]]
    (t/is (thrown-with-msg? ExceptionInfo
                            #"Can't use god tile during auction"
                            (ins/run-playbook env game playbook)))))

(t/deftest end-of-epoch-with-all-sun-disks-used-up
  (let [env      (start-env)
        game     (setup-game env 3)
        playbook [[0 :invoke-ra {}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :rand}]

                  [1 :invoke-ra {}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :rand}]
                  [1 :bid {:sun-disk :pass}]

                  [2 :invoke-ra {}]
                  [0 :bid {:sun-disk :rand}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk :pass}]

                  [0 :invoke-ra {}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :rand}]

                  [1 :invoke-ra {}]
                  [2 :bid {:sun-disk :pass}]
                  [1 :bid {:sun-disk :rand}]

                  [2 :invoke-ra {}]
                  [1 :bid {:sun-disk :rand}]
                  [2 :bid {:sun-disk :pass}]

                  [1 :invoke-ra {}]
                  [2 :bid {:sun-disk :pass}]
                  [1 :bid {:sun-disk :rand}]

                  [2 :invoke-ra {}]
                  [1 :bid {:sun-disk :rand}]
                  [2 :bid {:sun-disk :pass}]

                  [2 :invoke-ra {}]
                  [2 :bid {:sun-disk :rand}]

                  [2 :invoke-ra {}]
                  [2 :bid {:sun-disk :rand}]

                  [2 :invoke-ra {}]
                  [2 :bid {:sun-disk :rand}]

                  [2 :invoke-ra {}]
                  [2 :bid {:sun-disk :rand}]]]
    (ins/run-playbook env game playbook)
    (t/is (= true true))))

(t/deftest real-test
  (let [env      (start-env)
        game     (setup-game env 3)
        env      (assoc env
                        :first-seat (::hand/seat (::game/current-hand game)))
        env      (assoc env :game game)
        game     (ins/override-sun-disks env game {0 #{13 8 5 2}
                                                   1 #{12 9 6 3}
                                                   2 #{11 10 7 4}})
        playbook [[0 :draw {:tile tile/gold?}]
                  [1 :draw {:tile tile/nile?}]
                  [2 :draw {:tile tile/pharoah?}]
                  [0 :invoke-ra {}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk 4}]
                  [0 :bid {:sun-disk 5}]
                  [1 :draw {:tile tile/ra?}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :draw {:tile tile/pharoah?}]
                  [0 :draw {:tile tile/ra?}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :draw {:tile tile/funeral?}]
                  [2 :draw {:tile tile/nile?}]
                  [0 :draw {:tile tile/step-pyramid?}]
                  [1 :invoke-ra {}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :bid {:sun-disk 3}]
                  [1 :discard {:tiles [tile/pharoah?]}]
                  [2 :draw {:tile tile/art?}]
                  [0 :draw {:tile tile/pharoah?}]
                  [1 :draw {:tile tile/ra?}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :draw {:tile tile/palace?}]
                  [0 :draw {:tile tile/writing?}]
                  [1 :invoke-ra {}]
                  [2 :bid {:sun-disk 4}]
                  [0 :bid {:sun-disk 8}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :draw {:tile tile/ra?}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :draw {:tile tile/god?}]
                  [1 :draw {:tile tile/astronomy?}]
                  [2 :invoke-ra {}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk 4}]
                  [0 :draw {:tile tile/ra?}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :draw {:tile tile/nile?}]
                  [2 :draw {:tile tile/nile?}]
                  [0 :draw {:tile tile/nile?}]
                  [1 :invoke-ra {}]
                  [2 :bid {:sun-disk 7}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :bid {:sun-disk 9}]
                  [2 :draw {:tile tile/gold?}]
                  [0 :draw {:tile tile/art?}]
                  [1 :draw {:tile tile/flood?}]
                  [2 :invoke-ra {}]
                  [0 :bid {:sun-disk 2}]
                  [1 :bid {:sun-disk 6}]
                  [2 :bid {:sun-disk 7}]
                  [0 :draw {:tile tile/agriculture?}]
                  [1 :draw {:tile tile/earthquake?}]
                  [2 :use-god-tile {:tile tile/agriculture?}]
                  [0 :draw {:tile tile/ra?}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk :pass}]
                  [1 :draw {:tile tile/palace?}]
                  [2 :draw {:tile tile/writing?}]
                  [0 :draw {:tile tile/gold?}]
                  [1 :draw {:tile tile/statue?}]
                  [2 :invoke-ra {}]
                  [0 :bid {:sun-disk 2}]
                  [1 :bid {:sun-disk :pass}]
                  [2 :bid {:sun-disk 10}]
                  [2 :discard {:tiles [tile/palace? tile/statue?]}]
                  [0 :draw {:tile tile/pharoah?}]
                  [1 :draw {:tile tile/flood?}]
                  [2 :draw {:tile tile/obelisk?}]
                  [0 :invoke-ra {}]
                  [1 :bid {:sun-disk 12}]
                  [2 :bid {:sun-disk :pass}]
                  [0 :bid {:sun-disk 13}]
                  [1 :draw {:tile tile/obelisk?}]
                  [2 :draw {:tile tile/agriculture?}]
                  [0 :draw {:tile tile/art?}]
                  [1 :draw {:tile tile/temple?}]
                  [2 :invoke-ra {}]
                  [0 :bid {:sun-disk 2}]
                  [1 :bid {:sun-disk 12}]
                  [2 :bid {:sun-disk :pass}]]]
    (ins/run-playbook env game playbook)
    (t/is (= true true))))
