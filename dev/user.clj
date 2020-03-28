(ns user
  (:require [integrant.repl :refer [clear go halt prep init reset-all]]
            [hashp.core]
            [ra.pathom :as pathom]
            [ra.specs.game :as game]
            [ra.model.game :as m-game]
            [clojure.tools.namespace.repl :as repl]
            ra.integrant
            [ra.specs.player :as player]
            [ra.specs.hand :as hand]
            [ra.specs.epoch :as epoch]
            [ra.specs.tile :as tile]))

(repl/disable-reload! (find-ns 'user))

(defn reset []
  (integrant.repl/reset)
  :ok)

(integrant.repl/set-prep! ra.integrant/prep)

(defn s [] integrant.repl.state/system)

(defn c [] integrant.repl.state/config)

(defn current-player [parser game-id]
  (get-in (pathom/entity-parse
           parser
           `[{[::game/id ~game-id]
              [{::game/current-epoch [{::epoch/current-hand [{::hand/player [::player/id]}]}]}]}])
          [::game/current-epoch ::epoch/current-hand ::hand/player ::player/id]))

(comment
  (let [parser     (::pathom/parser (s))
        game-id    (::game/id (pathom/entity-parse parser `[(m-game/new-game {::game/num-players 1})]))
        player1-id (::player/id (pathom/entity-parse parser `[(m-player/new-player {::player/name "Moss"})]))
        player2-id (::player/id (pathom/entity-parse parser `[(m-player/new-player {::player/name "Bazza"})]))]
    (parser {} `[(m-game/join-game {::player/id ~player1-id ::game/id ~game-id})])
    (parser {} `[(m-game/join-game {::player/id ~player2-id ::game/id ~game-id})])
    (pathom/entity-parse parser `[(m-game/start-game {::game/id ~game-id})])
    (let [current-hand-player-id (current-player parser game-id)]

      (parser {} `[(m-game/draw-tile {::game/id ~game-id ::player/id ~current-hand-player-id})])
      (let [x (pathom/entity-parse
               parser
               `[{[::game/id ~game-id] [::game/id
                                        {::game/current-epoch [::epoch/number
                                                               ::epoch/current-sun-disk
                                                               {::epoch/current-hand [{::hand/player [::player/id]}]}
                                                               {::epoch/hands [::hand/sun-disks
                                                                               {::hand/tiles [::tile/title]}]}]}
                                        {::game/players [::player/name]}]}])]
        #p x))

    )

  )
