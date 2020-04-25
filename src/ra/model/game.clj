(ns ra.model.game
  (:require [com.fulcrologic.fulcro.networking.websocket-protocols
             :as
             fws-protocols]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [datascript.core :as d]
            [ghostwheel.core :as g]
            [integrant.core :as ig]
            [ra.core :refer [remove-keys update-when]]
            [ra.date :as date]
            [ra.db :as db]
            [ra.model.player :as m-player]
            [ra.model.tile :as m-tile]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.specs.auction.reason :as auction-reason]
            [ra.specs.epoch :as epoch]
            [ra.specs.game :as game]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules

;; Start Epoch
;; - current hand = player with highest sun disk
;; - Cannot be your turn if you have no tiles
;;
;; Actions
;; - Draw Tile
;; - Invoke Ra
;; - Spend God Tile
;;
;; Draw Tile
;; - If Ra token is drawn:
;;   - If Ra track becomes full, epoch ends. New epoch created, and that becomes current epoch
;;   - Start Auction minigame
;; - Regardless, it is the player to the left's turn (unless they have no sun disks)
;;
;; Invoke Ra
;; - Start Auction minigame
;; - Then it is the player to the left's turn
;;
;; Spend God tiles
;; - Can spend multiple god tiles at once
;; - Player to left's turn
;;
;; Auction
;; - Person who invoked Ra is "ra player"
;; - track reason for auction. draw tile, or voluntary
;; - need to track whether the auction track is full
;; - 1st player is to the left of the ra player
;; - Each player may wither bid or pass
;; - At end of auction, if no players have sun disks, epoch ends
;;
;; Current hand logic:
;; - if in auction, then
;;   - if no bids, left of ra player
;;   - else, loop until find player who can bid (has sun disk or sun disks > current bid)
;; - else
;;   - left of last hand to perform action. Store this when any action occurs

(def sun-disk-sets
  {2 [#{9 6 5 2}
      #{8 7 4 3}]
   3 [#{13 8 5 2}
      #{12 9 6 3}
      #{11 10 7 4}]
   4 [#{13 6 2}
      #{12 7 3}
      #{11 8 4}
      #{10 9 5}]
   5 [#{16 7 2}
      #{15 8 3}
      #{14 9 4}
      #{13 10 5}
      #{12 11 6}]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Small helpers

(defn hand->epoch [hand]
  (first (::epoch/_current-hand hand)))

(defn epoch->game [epoch]
  (first (::game/_current-epoch epoch)))

(defn hand->game [hand]
  (hand->epoch hand)
  (epoch->game (hand->epoch hand)))

(defn current-hand [game]
  (-> game
      ::game/current-epoch
      ::epoch/current-hand))

(defn game->players [game]
  (::game/players game))

(defn hand-turn? [hand]
  (= (:db/id hand)
     (:db/id (current-hand (hand->game hand)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bigger helpers

(defn find-all-tiles [db]
  (d/q '[:find [?t ...]
         :where [?t ::tile/type]]
       db))

(defn sample-tile [db game]
  (first
   (d/q '[:find (sample 1 ?tid) .
          :in $ ?gid
          :where [?gid ::game/tile-bag ?tid]]
        db (:db/id game))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resolvers

(pc/defresolver game-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::game/id]}]
  {::pc/input #{::game/id}
   ::pc/output [{::game/tile-bag m-tile/q}
                {::game/players m-player/q}
                ::game/started-at
                {::game/current-epoch [::epoch/number
                                       ::epoch/current-sun-disk
                                       {::epoch/auction [::auction/reason
                                                         {::auction/ra-hand [::hand/id]}
                                                         {::auction/bids [{::bid/hand [::hand/id]}
                                                                          ::bid/sun-disk]}]}
                                       ::epoch/in-auction?
                                       {::epoch/last-ra-invokee [{::hand/player [::player/id
                                                                                 ::player/name]}]}
                                       {::epoch/current-hand [{::hand/player [::player/id
                                                                              ::player/name]}]}
                                       {::epoch/hands [{::hand/tiles [::tile/title]}
                                                       ::hand/available-sun-disks
                                                       ::hand/used-sun-disks
                                                       ::hand/my-go?
                                                       {::hand/player [::player/id]}]}]}
                ::game/id]}
  (let [result (d/pull @conn parent-query [::game/id id])]
   (-> result
       ;; TODO add zdt encoding to websocket transit
       (update-when ::game/started-at str)
       (update ::game/current-epoch
               (fn [epoch]
                 (update epoch ::epoch/hands
                         (fn [hands]
                           (map (fn [hand]
                                  (assoc hand ::hand/my-go? (= (::hand/seat hand)
                                                               (::hand/seat (::epoch/current-hand epoch)))))
                                hands))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutations

(defn notify-other-clients [{:keys [connected-uids cid websockets]} game-id]
  (let [other-cids (disj (:any @connected-uids) cid)]
    (doseq [o other-cids]
      (fws-protocols/push websockets o :refresh {::game/id game-id}))))

(defn props->game-id [db props]
  (cond
    (::game/id props)  (::game/id props)
    (::epoch/id props) (::game/id (epoch->game (d/entity db [::epoch/id (::epoch/id props)])))
    (::hand/id props)  (::game/id (hand->game (d/entity db [::hand/id (::hand/id props)])))))

(defn notify-clients [{:keys [::pc/mutate] :as mutation}]
  (assoc mutation
         ::pc/mutate
         (fn [{:keys [::db/conn] :as env} params]
           (let [result (mutate env params)]
             (when-let [game-id (props->game-id @conn params)]
               (notify-other-clients env game-id))
             result))))

(pc/defmutation new-game [{:keys [::db/conn]} _]
  {::pc/params []
   ::pc/output [::game/id]}
  (let [tile-bag (find-all-tiles @conn)
        entity   {::game/id          (db/uuid)
                  ::game/tile-bag    tile-bag}]
    (d/transact! conn [entity])
    entity))

(pc/defmutation join-game [{:keys [::db/conn]}
                           {game-id ::game/id player-id ::player/id}]
  {::pc/params [::game/id ::player/id]
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (let [game (d/entity @conn [::game/id game-id])]
    (if (>= (count (game->players game)) 5)
      (throw (ex-info "Maximum players already reached" {}))
      (d/transact! conn [[:db/add [::game/id game-id] ::game/players [::player/id player-id]]])))
  {::game/id game-id})

(pc/defmutation start-game [{:keys [::db/conn]} {game-id ::game/id}]
  {::pc/params [::game/id]
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (let [game (d/entity @conn [::game/id game-id])]
    (if-let [started-at (::game/started-at game)]
      (throw (ex-info "Game already started" {:started-at started-at}))
      (let [num-players (count (game->players game))]
        (if (< num-players 2)
          (throw (ex-info "Need at least two players" {}))
          (let [id-atom      (atom -1)
                player-hands (map (fn [player sun-disks dbid i]
                                    {::hand/available-sun-disks sun-disks
                                     ::hand/player              (:db/id player)
                                     ::hand/id                  (db/uuid)
                                     ::hand/seat                i
                                     :db/id                     dbid})
                                  (::game/players (d/entity @conn [::game/id game-id]))
                                  (shuffle (get sun-disk-sets num-players))
                                  (repeatedly #(swap! id-atom dec))
                                  (range))
                epoch        {::epoch/id               (db/uuid)
                              ::epoch/number           1
                              ::epoch/current-sun-disk 1
                              ::epoch/current-hand     (:db/id (first (shuffle player-hands)))
                              ::epoch/hands            (map :db/id player-hands)
                              :db/id                   (swap! id-atom dec)}]
            (d/transact! conn (concat
                               player-hands
                               [epoch]
                               [[:db/add [::game/id game-id] ::game/started-at (date/zdt)]
                                [:db/add [::game/id game-id] ::game/current-epoch (:db/id epoch)]])))))))
  {::game/id game-id})

(defn draw-ra-tx [hand tile]
  (let [epoch (hand->epoch hand)
        game (epoch->game epoch)]
    [[:db/retract (:db/id game) ::game/tile-bag tile]
     [:db/add (:db/id epoch) ::epoch/ra-tiles tile]
     [:db/add (:db/id epoch) ::epoch/in-auction? true]
     [:db/add (:db/id epoch) ::epoch/last-ra-invokee (:db/id hand)]]))

(defn find-hand-by-seat [db epoch seat]
  (d/entity db (d/q '[:find ?hid .
                      :in $ ?epoch-id ?seat-num
                      :where [?epoch-id ::epoch/hands ?hid]
                      [?hid ::hand/seat ?seat-num]]
                    db
                    (:db/id epoch)
                    seat)))

(defn next-hand [current-hand]
  (let [db (d/entity-db current-hand)
        epoch (hand->epoch current-hand)
        game (hand->game current-hand)
        num-players (count (game->players game))]
    (loop [seat (inc (::hand/seat current-hand))]
      (if (>= seat num-players)
        (recur 0)
        (let [hand (find-hand-by-seat db epoch seat)]
          (if (empty? (::hand/available-sun-disks hand))
            (recur (inc seat))
            hand))))))

(defn draw-normal-tile-tx [hand tile]
  (let [epoch (hand->epoch hand)
        game (epoch->game epoch)]
    [[:db/retract (:db/id game) ::game/tile-bag tile]
     [:db/add (:db/id epoch) ::epoch/auction-tiles tile]
     [:db/add (:db/id epoch) ::epoch/current-hand (:db/id (next-hand hand))]]))

(pc/defmutation draw-tile [{:keys [::db/conn]} input]
  {::pc/params [::hand/id]
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (let [hand (d/entity @conn [::hand/id (::hand/id input)])
        game (hand->game hand)]
    (if (not (hand-turn? hand))
      (throw (ex-info "not your turn" {}))
      (let [tile (sample-tile @conn game)]
        (if (= (::tile/type (d/entity @conn tile)) ::tile-type/ra)
          (d/transact! conn (draw-ra-tx hand tile))
          (d/transact! conn (draw-normal-tile-tx hand tile)))
        {::game/id (::game/id (hand->game hand))}))))

(defn auction-track-full? [epoch]
  (= 8 (count (::epoch/auction-track epoch))))

(defn start-auction-tx [{:keys [hand auction-reason epoch]}]
  (let [auction-id -1]
    [[:db/add auction-id ::auction/ra-hand (:db/id hand)]
     [:db/add auction-id ::auction/reason auction-reason]
     [:db/add auction-id ::auction/track-full? (auction-track-full? epoch)]
     [:db/add (:db/id epoch) ::epoch/auction auction-id]]))

(defn current-hand-tx [epoch hand]
  [[:db/add (:db/id epoch) ::epoch/current-hand (:db/id (next-hand hand))]])

(pc/defmutation invoke-ra [{:keys [::db/conn]} input]
  {::pc/params #{::hand/id}
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (let [hand (d/entity @conn [::hand/id (::hand/id input)])
        epoch (hand->epoch hand)]
    (d/transact! conn (concat (start-auction-tx {:hand           hand
                                                 :auction-reason ::auction-reason/invoke
                                                 :epoch          epoch})
                              (current-hand-tx epoch hand)))
    {::game/id (::game/id (hand->game hand))}))

(defn play-bid-tx [{:keys [hand auction sun-disk]}]
  (let [bid-id -1]
    [[:db/add (:db/id auction) ::auction/bids bid-id]
     [:db/add bid-id ::bid/hand (:db/id hand)]
     (when sun-disk [:db/add bid-id ::bid/sun-disk sun-disk])
     [:db/retract (:db/id hand) ::hand/available-sun-disks sun-disk]]))

(pc/defmutation bid [{:keys [::db/conn]} {:keys [sun-disk] :as input}]
  {::pc/params    #{::hand/id :sun-disk}
   ::pc/transform notify-clients
   ::pc/output    [::game/id]}
  (let [hand    (d/entity @conn [::hand/id (::hand/id input)])
        epoch   (hand->epoch hand)
        auction (::epoch/auction epoch)]
    (if (not (::epoch/auction epoch))
      (throw (ex-info "Not in auction" {}))
      (d/transact! conn (concat
                         (play-bid-tx {:hand     hand
                                       :sun-disk sun-disk
                                       :auction  auction})
                         (current-hand-tx epoch hand))))
    {::game/id (::game/id (hand->game hand))}))

(defmethod ig/init-key ::ref-data [_ {:keys [::db/conn]}]
  (let [tiles (d/q '[:find ?t
                     :where [?t ::tile/type]]
                   @conn)]
    (when (empty? tiles)
      (d/transact! conn (m-tile/new-bag)))))

(def resolvers
  [bid
   draw-tile
   invoke-ra
   join-game
   new-game
   start-game

   game-resolver])
