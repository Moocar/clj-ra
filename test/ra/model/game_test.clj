(ns ra.model.game-test
  (:require [clojure.test :as t]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [datascript.core :as d]
            [ra.db :as db]
            [ra.model.game :as sut]
            [ra.model.player :as m-player]
            [ra.pathom :as pathom]
            [ra.specs.game :as game]
            [ra.specs.player :as player]))

(defn test-parser [{:keys [resolvers extra-env]}]
  (p/parser
   {::p/env     {::p/reader                 [p/map-reader
                                             pc/reader2
                                             pc/open-ident-reader
                                             p/env-placeholder-reader
                                             ]
                 ::p/placeholder-prefixes   #{">"}
                 ;; ::p/process-error          process-error
                 ::pc/mutation-join-globals [:tempids]
                 }
    ::p/mutate  pc/mutate
    ::p/plugins [(pc/connect-plugin {::pc/register resolvers})
                 (p/env-wrap-plugin #(merge % extra-env))
                 ;; p/error-handler-plugin
                 (p/post-process-parser-plugin p/elide-not-found)]}))

(defn new-game [p]
  (let [r (p {} [`(sut/new-game {})])]
    (get-in r [`sut/new-game ::game/id])))

(t/deftest win-disaster
  (let [resolvers (pathom/make-resolvers)
        conn      (d/create-conn db/schema)
        extra-env {::db/conn conn}
        parser    (test-parser {:resolvers resolvers :extra-env extra-env})
        p1-id "1"
        p2-id "2"]
    (parser {} [`(m-player/new-player {::player/id ~p1-id})])
    (parser {} [`(m-player/new-player {::player/id ~p2-id})])
    (let [game-id #p (new-game parser)]
      (parser {} [`(sut/join-game {::game/id ~game-id ::player/id ~p1-id})])
      (parser {} [`(sut/join-game {::game/id ~game-id ::player/id ~p2-id})])
      (parser {} [`(sut/start-game {::game/id ~game-id})]))
    (t/is (= 4 (+ 2 2)))))
