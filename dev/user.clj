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
            [ra.specs.tile :as tile]
            [datascript.core :as d]
            [ra.core :refer [uuid]]
            [ra.db :as db]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]))

(repl/disable-reload! (find-ns 'user))

(repl/set-refresh-dirs "src")

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

(defn print-game [{::game/keys [current-epoch id] :as g}]
  (println "game-id: " id)
  (let [{::epoch/keys [auction current-sun-disk number hands current-hand] :as epoch} (d/touch current-epoch)]
    (println "current epoch:" number)
    (println "current-sun disk:" current-sun-disk)
    (println "current-hand" (::hand/seat current-hand))
    (println)
    (when auction
      (let [{::auction/keys [ra-hand reason tiles-full? bids]} auction]
        (println "Auction")
        (println (d/touch auction))
        (println "Ra hand" (::hand/seat ra-hand))
        (println "Ra reason" reason)
        (println "tiles full?" tiles-full?)
        (println "Bids" (map (fn [{bid-hand ::bid/hand sun-disk ::bid/sun-disk}]
                               [(::hand/seat bid-hand) sun-disk])
                             bids))
        (println)))
    (println "hands")
    (doseq [hand (sort-by ::hand/seat hands)]
      (let [{::hand/keys [available-sun-disks seat] :as hand} (d/touch hand)]
        (println "------hand" seat)
        (println "available sun disks:" (sort (vec available-sun-disks))))))
  (println ))

(defn d [game-id]
  (some-> (datascript.core/touch (datascript.core/entity @(:ra.db/conn (s)) [:ra.specs.game/id (ra.core/uuid game-id)]))
          ;; :ra.specs.game/current-epoch
          ;; d/touch
;;          :ra.specs.epoch/current-hand
          ;;          d/touch
          ;; :ra.specs.epoch/auction
          ;;d/touch
          ))

(comment
  (let [parser     (::pathom/parser (s))
        game-id    (::game/id (pathom/entity-parse parser `[(m-game/new-game {::game/num-players 1})]))
        player1-id (::player/id (pathom/entity-parse parser `[(m-player/new-player {::player/name "Moss"})]))
        player2-id (::player/id (pathom/entity-parse parser `[(m-player/new-player {::player/name "Bazza"})]))]
    (parser {} `[(m-game/join-game {::player/id ~player1-id ::game/id ~game-id})])
    (parser {} `[(m-game/join-game {::player/id ~player2-id ::game/id ~game-id})])
    (pathom/entity-parse parser `[(m-game/start-game {::game/id ~game-id})])
    (let [current-hand-player-id (current-player parser game-id)]

      (let [x (pathom/entity-parse
               parser
               `[{[::game/id ~game-id] [::game/id
                                        {::game/current-epoch [::epoch/number
                                                               ::epoch/current-sun-disk
                                                               {::epoch/auction-tiles [::tile/title]}
                                                               {::epoch/last-ra-invokee [{::hand/player [::player/name]}]}
                                                               {::epoch/current-hand [{::hand/player [::player/id]}
                                                                                      ::hand/seat]}
                                                               {::epoch/hands [::hand/available-sun-disks
                                                                               {::hand/tiles [::tile/title]}]}]}
                                        {::game/players [::player/name]}]}])]
        x)
      (parser {} `[(m-game/draw-tile {::game/id ~game-id ::player/id ~current-hand-player-id})])
      (let [x (pathom/entity-parse
               parser
               `[{[::game/id ~game-id] [::game/id
                                        {::game/current-epoch [::epoch/number
                                                               ::epoch/current-sun-disk
                                                               ::epoch/in-auction?
                                                               {::epoch/auction-tiles [::tile/title]}
                                                               {::epoch/last-ra-invokee [{::hand/player [::player/name]}]}
                                                               {::epoch/current-hand [{::hand/player [::player/id]}
                                                                                      ::hand/seat]}
                                                               {::epoch/hands [::hand/available-sun-disks
                                                                               {::hand/tiles [::tile/title]}]}]}
                                        {::game/players [::player/name]}]}])]
        x))

    )



  )
