(ns ra.model.game
  (:require [clojure.set :as set]
            [com.fulcrologic.fulcro.networking.websocket-protocols
             :as
             fws-protocols]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [datascript.core :as d]
            [integrant.core :as ig]
            [ra.core :refer [update-when]]
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
;; Disaster
;; - If win auction
;; - take all tiles
;; - For each disaster tile
;;   - ask user to select two of disaster tile type
;;   - Each gets discarded
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

(def ras-per-epoch
  {2 6
   3 8
   4 9
   5 10})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scoring

(defn score-hands [hands]
  (let [hand-scores (map (fn [hand]
                           {::hand/id      (::hand/id hand)
                            :db/id         (:db/id hand)
                            :old-score     (::hand/score hand)
                            :score         (let [tiles             (::hand/tiles hand)
                                                 god-count         (count (filter m-tile/god? tiles))
                                                 gold-count        (count (filter m-tile/gold? tiles))
                                                 unique-civs-count (count (distinct (map ::tile/civilization-type (filter m-tile/civ? tiles))))
                                                 flood-count       (count (filter m-tile/flood? tiles))
                                                 nile-count        (count (filter m-tile/nile? tiles))]
                                             (+ 0
                                                (* 2 god-count)
                                                (* 3 gold-count)
                                                (case unique-civs-count
                                                  0 -5
                                                  1 0
                                                  2 5
                                                  3 10
                                                  4 15)
                                                (if (pos? flood-count)
                                                  (+ flood-count nile-count)
                                                  0)))
                            :pharoah-count (count (filter m-tile/pharoah? (::hand/tiles hand)))})
                         hands)
        sorted-by-pharoah (sort-by :pharoah-count hands)]
    (if (= (:pharoah-count (first sorted-by-pharoah))
           (:pharoah-count (last sorted-by-pharoah)))
      hand-scores
      (map (fn [hand]
             (cond (= hand (first sorted-by-pharoah))
                   (update hand :score - 2)
                   (= hand (last sorted-by-pharoah))
                   (update hand :score + 5)
                   :else
                   hand))
           hand-scores))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Small helpers

(defn hand->epoch [hand]
  (last (sort-by ::epoch/number (::epoch/_current-hand hand))))

(defn epoch->game [epoch]
  (first (::game/_current-epoch epoch)))

(defn hand->game [hand]
  (epoch->game (hand->epoch hand)))

(defn current-hand [game]
  (-> game
      ::game/current-epoch
      ::epoch/current-hand))

(defn game->players [game]
  (::game/players game))

(defn auction->epoch [auction]
  (first (::epoch/_auction auction)))

(defn auction->game [auction]
  (epoch->game (auction->epoch auction)))

(defn hand-turn? [hand]
  (= (:db/id hand)
     (:db/id (current-hand (hand->game hand)))))


(defn move-thing-tx
  "Moves something from from to to"
  [thing from to]
  (let [[from-entity from-attr] from
        [to-entity to-attr] to]
    [[:db/retract (:db/id from-entity) from-attr thing]
     [:db/add (:db/id to-entity) to-attr thing]]))

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
                                                         ::auction/tiles-full?
                                                         {::auction/ra-hand [::hand/id]}
                                                         {::auction/bids [{::bid/hand [::hand/id]}
                                                                          ::bid/sun-disk]}]}
                                       ::epoch/in-auction?
                                       ::epoch/in-disaster?
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
                  (-> epoch
                      (update ::epoch/auction-tiles #(sort-by :db/id %))
                      (update ::epoch/hands
                              (fn [hands]
                                (map (fn [hand]
                                       (-> hand
                                           (assoc ::hand/my-go? (= (::hand/seat hand)
                                                                   (::hand/seat (::epoch/current-hand epoch))))))
                                     hands)))))))))

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
         (fn [env params]
           (let [result (mutate env params)]
             (when (:connected-uids env)
               (when-let [game-id (::game/id result)]
                 (notify-other-clients env game-id)))
             result))))

(defn do-new-game [conn game-id]
  (let [tile-bag (find-all-tiles @conn)
        entity   {::game/id       game-id
                  ::game/tile-bag tile-bag}]
    (d/transact! conn [entity])
    entity))

(defn do-clear-game [conn game-id]
  (d/transact! conn
               (concat
                [[:db/retract [::game/id game-id] ::game/started-at]
                 [:db/retract [::game/id game-id] ::game/tile-bag]
                 [:db/retract [::game/id game-id] ::game/epochs]
                 [:db/retract [::game/id game-id] ::game/current-epoch]]
                (let [tiles (find-all-tiles @conn)]
                  (mapv (fn [tile-id]
                          [:db/add [::game/id game-id] ::game/tile-bag tile-id])
                        tiles)))))

(pc/defmutation new-game [{:keys [::db/conn]} _]
  {::pc/params []
   ::pc/output [::game/id]}
  (do-new-game conn (db/uuid)))

(pc/defmutation join-game [{:keys [::db/conn]}
                           {game-id ::game/id player-id ::player/id}]
  {::pc/params [::game/id ::player/id]
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (let [game (d/entity @conn [::game/id game-id])]
    (when (::game/started-at game)
      (throw (ex-info "Game already started" {})))
    (if (>= (count (game->players game)) 5)
      (throw (ex-info "Maximum players already reached" {}))
      (d/transact! conn [[:db/add [::game/id game-id] ::game/players [::player/id player-id]]])))
  {::game/id game-id})

(defn do-start-game [conn game-id]
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
                                     ::hand/score               5
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
                                [:db/add [::game/id game-id] ::game/current-epoch (:db/id epoch)]
                                [:db/add [::game/id game-id] ::game/epochs (:db/id epoch)]]))))))))

(pc/defmutation start-game [{:keys [::db/conn]} {game-id ::game/id}]
  {::pc/params [::game/id]
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (do-start-game conn game-id)
  {::game/id game-id})

(pc/defmutation reset [{:keys [::db/conn]} {game-id ::game/id}]
  {::pc/params [::game/id]
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (do-clear-game conn game-id)
  (do-start-game conn game-id)
  {::game/id game-id})

(defn find-hand-by-seat [db epoch seat]
  (d/entity db (d/q '[:find ?hid .
                      :in $ ?epoch-id ?seat-num
                      :where [?epoch-id ::epoch/hands ?hid]
                      [?hid ::hand/seat ?seat-num]]
                    db
                    (:db/id epoch)
                    seat)))

(defn next-hand
  "Returns the hand to the left of the given hand"
  [current-hand]
  (assert current-hand)
  (let [db (d/entity-db current-hand)
        _ (assert db)
        epoch (hand->epoch current-hand)
        _ (assert epoch)
        game (hand->game current-hand)
        _ (assert game)
        num-players (count (game->players game))]
    (assert (pos? num-players))
    (loop [seat (inc (::hand/seat current-hand))]
      (if (>= seat num-players)
        (recur 0)
        (let [hand (find-hand-by-seat db epoch seat)]
          (if (empty? (::hand/available-sun-disks hand))
            (recur (inc seat))
            hand))))))

(defn rotate-current-hand-tx
  "Sets the epoch's current hand to the player to the left of the given hand"
  [epoch hand]
  (assert epoch)
  (assert hand)
  [[:db/add (:db/id epoch) ::epoch/current-hand (:db/id (next-hand hand))]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Invoke Ra

(defn discard-auction-tiles-op
  "Removes the auction tiles from the epoch"
  [epoch]
  [:db/retract (:db/id epoch) ::epoch/auction-tiles])

(defn discard-ra-tiles-op
  "Removes the ra tiles from the epoch"
  [epoch]
  [:db/retract (:db/id epoch) ::epoch/ra-tiles])

(defn auction-tiles-full?
  "Returns true if the auction track is full"
  [epoch]
  (= 8 (count (::epoch/auction-tiles epoch))))

(defn start-auction-tx
  "Create an auction and adds it to the epoch"
  [{:keys [hand auction-reason epoch]}]
  (let [auction-id -1]
    [[:db/add auction-id ::auction/ra-hand (:db/id hand)]
     [:db/add auction-id ::auction/reason auction-reason]
     [:db/add auction-id ::auction/tiles-full? (auction-tiles-full? epoch)]
     [:db/add (:db/id epoch) ::epoch/last-ra-invoker (:db/id hand)]
     [:db/add (:db/id epoch) ::epoch/auction auction-id]]))

(defn invoke-ra-tx [hand reason]
  (let [epoch (hand->epoch hand)]
    (concat (start-auction-tx {:hand           hand
                               :auction-reason reason
                               :epoch          epoch})
            (rotate-current-hand-tx epoch hand))))

(pc/defmutation invoke-ra [{:keys [::db/conn]} input]
  {::pc/params #{::hand/id}
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (assert (::hand/id input))
  (let [hand (d/entity @conn [::hand/id (::hand/id input)])]
    (assert hand)
    (assert (hand->epoch hand))
    (d/transact! conn (invoke-ra-tx hand ::auction-reason/invoke))
    {::game/id (::game/id (hand->game hand))}))

(defn max-ras [game]
  (get ras-per-epoch (count (game->players game))))

(defn last-ra? [epoch]
  (= (inc (count (::epoch/ra-tiles epoch)))
     (max-ras (epoch->game epoch))))

(defn flip-sun-disks-tx [hand]
  (mapcat (fn [sun-disk]
            (move-thing-tx sun-disk
                           [hand ::hand/used-sun-disks]
                           [hand ::hand/available-sun-disks]))
          (::hand/used-sun-disks hand)))

(defn discard-non-scarabs-tx [hand]
  (->> (::hand/tiles hand)
       (remove ::tile/scarab?)
       (map (fn [tile]
              [:db/retract (:db/id hand) ::hand/tiles (:db/id tile)]))))

(defn finish-epoch-tx [epoch]
  (let [game         (epoch->game epoch)
        hand-scores  (score-hands (::epoch/hands epoch))
        id-atom      (atom -1)
        new-epoch-id (swap! id-atom dec)]
    (concat (mapv (fn [hand]
                    [:db/add (:db/id hand) ::hand/score (+ (:old-score hand) (:score hand))])
                  hand-scores)
            (mapcat flip-sun-disks-tx (::epoch/hands epoch))
            (mapcat discard-non-scarabs-tx (::epoch/hands epoch))
            [[:db/add (:db/id game) ::game/current-epoch new-epoch-id]
             {:db/id new-epoch-id
              ::epoch/current-hand (:db/id (::epoch/current-hand epoch))
              ::epoch/current-sun-disk (::epoch/current-sun-disk epoch)
              ::epoch/hands (map :db/id hand-scores)
              ::epoch/id (db/uuid)
              ::epoch/number (inc (::epoch/number epoch))}
             [:db/retract (:db/id epoch) ::epoch/current-hand]
             [:db/add (:db/id game) ::game/current-epoch new-epoch-id]
             [:db/add (:db/id game) ::game/epochs new-epoch-id]])))

(defn draw-ra-tx [hand tile]
  (let [epoch (hand->epoch hand)
        game  (epoch->game epoch)]
    (concat
     (move-thing-tx (:db/id tile) [game ::game/tile-bag] [epoch ::epoch/ra-tiles])
     [[:db/add (:db/id epoch) ::epoch/in-auction? true]]
     (invoke-ra-tx hand ::auction-reason/draw)
     (when (last-ra? epoch)
       (finish-epoch-tx epoch)))))

(defn draw-normal-tile-tx [hand tile]
  (let [epoch (hand->epoch hand)
        game  (epoch->game epoch)
        last-tile (last (sort-by ::tile/auction-track-position (::epoch/auction-tiles epoch)))]
    (concat
     (move-thing-tx (:db/id tile) [game ::game/tile-bag] [epoch ::epoch/auction-tiles])
     [[:db/add (:db/id epoch) ::epoch/current-hand (:db/id (next-hand hand))]
      [:db/add (:db/id tile) ::tile/auction-track-position (inc (or (::tile/auction-track-position last-tile) 0))]])))

(defn do-draw-tile [conn hand tile]
  (assert hand)
  (assert tile)
  (let [epoch (hand->epoch hand)]
    (when (::epoch/in-disaster? epoch)
      (throw (ex-info "Waiting for players to discard disaster tiles" {})))
    (if (not (hand-turn? hand))
      (throw (ex-info "not your turn" {:current-hand (::epoch/current-hand epoch)
                                       :tried-hand   hand}))
      (if (m-tile/ra? tile)
        (d/transact! conn (draw-ra-tx hand tile))
        (d/transact! conn (draw-normal-tile-tx hand tile))))))

(pc/defmutation draw-tile [{:keys [::db/conn]} input]
  {::pc/params [::hand/id]
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (let [hand (d/entity @conn [::hand/id (::hand/id input)])
        epoch (hand->epoch hand)
        game (hand->game hand)
        tile (d/entity @conn (sample-tile @conn game))]
    (when (auction-tiles-full? epoch)
      (throw (ex-info "Auction Track full" {})))
    (do-draw-tile conn hand tile)
    {::game/id (::game/id (hand->game hand))}))

(defn waiting-on-last-bid?
  "Returns true if the current bid auction's bid is the last"
  [auction]
  (let [epoch (auction->epoch auction)
        active-hands (filter (fn [hand]
                               (or (seq (::hand/available-sun-disks hand))
                                   (some #(= hand %) (map ::bid/hand (::auction/bids auction)))))
                             (::epoch/hands epoch))]
    (= (inc (count (::auction/bids auction)))
       (count active-hands))))

(defn winning-bid
  "Given an auction and a potential new bid, return the winning bid (which could
  be the new bid, or an existing on in the auction"
  [auction new-bid]
  (->> new-bid
       (conj (::auction/bids auction))
       (remove #(nil? (::bid/sun-disk %)))
       (sort-by ::bid/sun-disk)
       (last)))

(defn not-winning-bid
  "Returns true if bid is nil or not the winning bid"
  [bid winning-bid]
  (and (not= winning-bid bid)
       (not (nil? (::bid/sun-disk bid)))))

(defn auction-tiles->hand-tx
  "Move all auction track tiles to the given hand"
  [epoch hand]
  (concat
   ;; Clear the auction track
   [[:db/retract (:db/id epoch) ::epoch/auction-tiles]]
   ;; Move all tiles in the auction track to the given hand
   (mapv (fn [tile]
           [:db/add (:db/id hand) ::hand/tiles (:db/id tile)])
         (::epoch/auction-tiles epoch))))

(defn sun-disks-in-play? [epoch]
  (some (fn [hand]
            (seq (::hand/available-sun-disks hand)))
        (::epoch/hands epoch)))

(defn bid-tx
  "Moves the sun-disk from the hand to the middle of the board and triggers an end
  to the auction if it's the last bid"
  [hand sun-disk]
  (let [epoch (hand->epoch hand)
        auction (::epoch/auction epoch)]
    (concat

     ;; Add new bid to auction and move sun disk from hand to auction
     (let [bid-id -1]
       (concat
        [[:db/add (:db/id auction) ::auction/bids bid-id]
         [:db/add bid-id ::bid/hand (:db/id hand)]]
        (when sun-disk
          (move-thing-tx sun-disk
                         [hand ::hand/available-sun-disks]
                         [{:db/id bid-id} ::bid/sun-disk]))))

     ;; If the next bid would be the winner, then trigger an end to the auction
     (let [new-bid     {::bid/hand     hand
                        ::bid/sun-disk sun-disk}
           winning-bid (winning-bid auction new-bid)]
       (concat
        (when (waiting-on-last-bid? auction)
          (let [other-bids (filter #(not-winning-bid % winning-bid)
                                      (conj (::auction/bids auction) new-bid))]
            (concat
             ;; Put all the non-winning bids back in the hand
             (mapv (fn [bid]
                     [:db/add (:db/id (::bid/hand bid)) ::hand/available-sun-disks (::bid/sun-disk bid)])
                   other-bids)
             ;; Remove the auction from the epoch (it's done)
             ;; TODO have flag "in auction" so frontend can show last bid that was played
             [[:db/retract (:db/id epoch) ::epoch/auction (:db/id auction)]]

             ;; If there was a winning bid
             (if winning-bid
               (concat
                ;; Move auction track tiles to winning hand
                (auction-tiles->hand-tx epoch (::bid/hand winning-bid))
                [ ;; Move the epoch's sun-disk into the winning hand as a used disk
                 [:db/add (:db/id (::bid/hand winning-bid)) ::hand/used-sun-disks (::epoch/current-sun-disk epoch)]
                 ;; Set the current epoch's sun disk (middle of the board) to the
                 ;; winning disk
                 [:db/add (:db/id epoch) ::epoch/current-sun-disk (::bid/sun-disk winning-bid)]]
                ;; If the player picks up a disaster tile, go into disaster resolution mode
                (when (some ::tile/disaster? (::epoch/auction-tiles epoch))
                  [[:db/add (:db/id epoch) ::epoch/in-disaster? true]]))

               ;; If everyone passed
               (if (::auction/tiles-full? auction)
                 ;; If the auction track is full, then we discard all the tiles
                 [(discard-auction-tiles-op epoch)]
                 ;; If the auction track isn't full, and everyone else passed, you have to bid
                 (when (= ::auction-reason/invoke (::auction/reason auction))
                   (throw (ex-info "You voluntarily invoked ra. You must bid" {}))))))))

        ;; TODO If they win a disaster tile, but they have no choices, then
        ;; discard for them and move to the next hand

        ;; Calculate whose turn it is next. If it was a winning bid and they won
        ;; a disaster, then it's that person's turn still. If not, it's the
        ;; player to the left of the last ra invoker
        (let [the-next-hand (if (and (waiting-on-last-bid? auction)
                                     winning-bid
                                     (some ::tile/disaster? (::epoch/auction-tiles epoch)))
                              hand
                              (if (and (waiting-on-last-bid? auction) winning-bid)
                                (next-hand (::epoch/last-ra-invoker epoch))
                                (next-hand hand)))]
          [[:db/add (:db/id epoch) ::epoch/current-hand (:db/id the-next-hand)]]))))))

(pc/defmutation bid
  [{:keys [::db/conn]} {:keys [sun-disk] :as input}]
  {::pc/params    #{::hand/id :sun-disk}
   ::pc/transform notify-clients
   ::pc/output    [::game/id]}
  (assert (contains? input :sun-disk))
  (assert (::hand/id input))
  (let [hand  (d/entity @conn [::hand/id (::hand/id input)])
        epoch (hand->epoch hand)]
    (assert (= hand (::epoch/current-hand epoch)))
    (when-not (::epoch/auction epoch)
      (throw (ex-info "Not in an auction" {})))
    ;; Perform a fake tx to find out if we have run out of sun disks. And if so,
    ;; finish the epoch
    (let [tx (bid-tx hand sun-disk)
          db-after (:db-after (d/with @conn tx))
          new-epoch (d/entity db-after (:db/id epoch))
          tx (if (sun-disks-in-play? new-epoch)
               tx
               (concat tx (finish-epoch-tx new-epoch)))]
      (d/transact! conn tx))
    {::game/id (::game/id (hand->game hand))}))

(defn discard-tile-op [hand tile]
  [:db/retract (:db/id hand) ::hand/tiles (:db/id tile)])

(pc/defmutation discard-disaster-tiles
  [{:keys [::db/conn]} input]
  {::pc/params    #{::hand/id :tile-ids}
   ::pc/transform notify-clients
   ::pc/output    [::game/id]}
  (let [hand                 (d/entity @conn [::hand/id (::hand/id input)])
        epoch                (hand->epoch hand)
        disaster-tiles       (set (filter ::tile/disaster? (::hand/tiles hand)))
        selected-tiles       (set (map (fn [tile-id] (d/entity @conn [::tile/id tile-id])) (:tile-ids input)))
        possible-tiles       (set(filter (fn [tile]
                                     (and (not (::tile/disaster? tile))
                                          ((set (map ::tile/type disaster-tiles)) (::tile/type tile))))
                                   (::hand/tiles hand)))
        disaster-type->count (->> disaster-tiles
                                  (group-by ::tile/type)
                                  (reduce-kv (fn [a k v]
                                               (assoc a k (count v)))
                                             {}))]
    (when-not (seq disaster-tiles)
      (throw (ex-info "No disaster tiles in hand" {})))

    (when-let [drought-tiles (seq (filter m-tile/drought? disaster-tiles))]
      (let [flood-tiles-in-hand (filter m-tile/flood? (::hand/tiles hand))
            flood-tiles-selected (filter m-tile/flood? selected-tiles)
            needed (min (count flood-tiles-in-hand) (* (count drought-tiles) 2))]
        (when (< (count flood-tiles-selected) needed)
          (throw (ex-info "Need to select more flood tiles" {})))))

    (doseq [[disaster-type disaster-count] disaster-type->count]
      (let [candidates     (set (filter #(= disaster-type (::tile/type %)) possible-tiles))
            expected-count (min (count candidates) (* disaster-count 2))
            selected       (set (filter #(= disaster-type (::tile/type %)) selected-tiles))]
        (when (not= expected-count (count selected))
          (throw (ex-info "Invalid number of disaster tiles to discard"
                          {:expected-count expected-count
                           :received       (count selected)})))
        (when-not (set/subset? selected candidates)
          (throw (ex-info "Invalid selected disaster tiles" {})))))

    (d/transact! conn (concat (mapv #(discard-tile-op hand %)
                                    (set/union selected-tiles disaster-tiles))
                              [[:db/add (:db/id epoch) ::epoch/in-disaster? false]
                               [:db/add (:db/id epoch) ::epoch/current-hand (:db/id (next-hand (::epoch/last-ra-invoker epoch)))]]))
    {::game/id (::game/id (hand->game hand))}))

(pc/defmutation use-god-tile
  [{:keys [::db/conn]} input]
  {::pc/params    #{::hand/id :god-tile-id :auction-track-tile-id}
   ::pc/transform notify-clients
   ::pc/output    [::game/id]}
  (let [db @conn
        god-tile (d/entity db [::tile/id (:god-tile-id input)])
        hand (d/entity db [::hand/id (::hand/id input)])
        auction-track-tile (d/entity @conn [::tile/id (:auction-track-tile-id input)])
        epoch (hand->epoch hand)]
    (when (= (::tile/type auction-track-tile) ::tile-type/god)
      (throw (ex-info "Can't use god tile on a god tile" {})))
    (d/transact! conn (concat
                       [[:db/retract (:db/id hand) ::hand/tiles (:db/id god-tile)]]
                       (move-thing-tx (:db/id auction-track-tile) [epoch ::epoch/auction-tiles] [hand ::hand/tiles])))
    {::game/id (::game/id (hand->game hand))}))

(defmethod ig/init-key ::ref-data [_ {:keys [::db/conn]}]
  (let [tiles (d/q '[:find ?t
                     :where [?t ::tile/type]]
                   @conn)]
    (when (empty? tiles)
      (d/transact! conn (m-tile/new-bag)))))

(def resolvers
  [
   bid
   discard-disaster-tiles
   draw-tile
   invoke-ra
   join-game
   new-game
   reset
   start-game
   use-god-tile

   game-resolver])
