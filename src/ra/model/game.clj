(ns ra.model.game
  (:require [clojure.set :as set]
            [com.fulcrologic.fulcro.networking.websocket-protocols
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
            [ra.specs.tile.river :as river]
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
                            :score
                            (->> (::hand/tiles hand)
                                 (group-by ::tile/type)
                                 (reduce-kv (fn [a tile-type tiles]
                                              (+ a
                                                 (case tile-type
                                                   ::tile-type/god          (* 2 (count tiles))
                                                   ::tile-type/gold         (* 3 (count tiles))
                                                   ::tile-type/civilization (case (count (group-by ::tile/civilization-type tiles))
                                                                              0 -5
                                                                              1 0
                                                                              2 5
                                                                              3 10
                                                                              4 15)
                                                   ::tile-type/river        (let [x (group-by ::tile/river-type tiles)]
                                                                              (if-not (seq (::river/flood x))
                                                                                0
                                                                                (reduce + (map count (vals x)))))
                                                   0)))
                                            0))
                            :pharoah-count (count (filter #(= ::tile-type/pharoah (::tile/type %))
                                                          (::hand/tiles hand)))})
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

(defn tile->hand [tile]
  (first (::hand/_tiles tile)))

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
                 [:db/retract [::game/id game-id] ::game/current-epoch]]
                (mapv (fn [tile-id]
                             [:db/add [::game/id game-id] ::game/tile-bag tile-id])
                            (find-all-tiles @conn)))))

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

(defn rotate-current-hand-tx
  "Sets the epoch's current hand to the player to the left of the given hand"
  [epoch hand]
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
  (let [hand (d/entity @conn [::hand/id (::hand/id input)])]
    (d/transact! conn (invoke-ra-tx hand ::auction-reason/invoke))
    {::game/id (::game/id (hand->game hand))}))

(defn move-tile-tx [tile from to]
  (let [[from-entity from-attr] from
        [to-entity to-attr]     to]
    [[:db/retract (:db/id from-entity) from-attr (:db/id tile)]
     [:db/add (:db/id to-entity) to-attr (:db/id tile)]]))

(defn max-ras [game]
  (get ras-per-epoch (count (game->players game))))

(defn last-ra? [epoch]
  (= (inc (count (::epoch/ra-tiles epoch)))
     (max-ras (epoch->game epoch))))

(defn finish-epoch-tx [epoch]
  (let [game         (epoch->game epoch)
        hand-scores  (score-hands (::epoch/hands epoch))
        id-atom      (atom -1)
        new-epoch-id (swap! id-atom dec)]
    (concat (mapv (fn [hand]
                    [:db/add (:db/id hand) ::hand/score (+ (:old-score hand) (:score hand))])
                  hand-scores)
            [[:db/add (:db/id game) ::game/current-epoch new-epoch-id]
             {:db/id new-epoch-id
              ::epoch/current-hand (:db/id (next-hand (::epoch/current-hand epoch)))
              ::epoch/current-sun-disk (::epoch/current-sun-disk epoch)
              ::epoch/hands (map :db/id hand-scores)
              ::epoch/id (db/uuid)
              ::epoch/number (inc (::epoch/number epoch))}
             [:db/retract (:db/id epoch) ::epoch/current-hand (:db/id (::epoch/current-hand epoch))]
             [:db/add (:db/id game) ::game/current-epoch new-epoch-id]
             [:db/add (:db/id game) ::game/epochs new-epoch-id]])))

(defn draw-ra-tx [hand tile]
  (let [epoch (hand->epoch hand)
        game  (epoch->game epoch)]
    (concat
     (move-tile-tx tile [game ::game/tile-bag] [epoch ::epoch/ra-tiles])
     [[:db/add (:db/id epoch) ::epoch/in-auction? true]]
     (invoke-ra-tx hand ::auction-reason/draw)
     (when (last-ra? epoch)
       (finish-epoch-tx epoch)))))

(defn draw-normal-tile-tx [hand tile]
  (let [epoch (hand->epoch hand)
        game  (epoch->game epoch)
        last-tile (last (sort-by ::tile/auction-track-position (::epoch/auction-tiles epoch)))]
    (concat
     (move-tile-tx tile [game ::game/tile-bag] [epoch ::epoch/auction-tiles])
     [[:db/add (:db/id epoch) ::epoch/current-hand (:db/id (next-hand hand))]
      [:db/add (:db/id tile) ::tile/auction-track-position (inc (or (::tile/auction-track-position last-tile) 0))]])))

(pc/defmutation draw-tile [{:keys [::db/conn]} input]
  {::pc/params [::hand/id]
   ::pc/transform notify-clients
   ::pc/output [::game/id]}
  (let [hand (d/entity @conn [::hand/id (::hand/id input)])
        game (hand->game hand)
        epoch (hand->epoch hand)]
    (when (::epoch/in-disaster? epoch)
      (throw (ex-info "Waiting for players to discard disaster tiles" {})))
    (if (not (hand-turn? hand))
      (throw (ex-info "not your turn" {:current-hand (::epoch/current-hand epoch)
                                       :tried-hand hand}))
      (let [tile (d/entity @conn (sample-tile @conn game))]
        (if (= (::tile/type tile) ::tile-type/ra)
          (d/transact! conn (draw-ra-tx hand tile))
          (d/transact! conn (draw-normal-tile-tx hand tile)))
        {::game/id (::game/id (hand->game hand))}))))

(defn move-sun-disk-tx
  "Moves a sun-disk from from to to"
  [sun-disk from to]
  (let [[from-entity from-attr] from
        [to-entity to-attr] to]
    [[:db/retract (:db/id from-entity) from-attr sun-disk]
     [:db/add (:db/id to-entity) to-attr sun-disk]]))

(defn play-bid-tx
  "Adds a new bid to the auction. The sun-disk is moved from the hand to the bid"
  [{:keys [hand auction sun-disk]}]
  (let [bid-id -1]
    (concat
     [[:db/add (:db/id auction) ::auction/bids bid-id]
      [:db/add bid-id ::bid/hand (:db/id hand)]]
     (when sun-disk
       (move-sun-disk-tx sun-disk
                         [hand ::hand/available-sun-disks]
                         [{:db/id bid-id} ::bid/sun-disk])))))

(defn waiting-on-last-bid?
  "Returns true if the current bid auction's bid is the last"
  [auction]
  (let [game (auction->game auction)
        num-players (count (game->players game))]
    (= (inc (count (::auction/bids auction))) num-players)))

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

(defn last-bid-tx
  "The auction ends.
  1. Move all non-winning bids back into their original hands
  2. Remove auction from the epoch
  3. Move auction track tiles to winning hand
  4. Move the epoch's current sun-disk into the winning hand as a used disk
  5. Set the epoch's current disk to the winning bid's

  If there is no winning bid, then discard all tiles from the auction track"
  [{:keys [auction new-bid]}]
  (let [epoch (auction->epoch auction)
        winning-bid (winning-bid auction new-bid)
        other-bids  (filter #(not-winning-bid % winning-bid)
                            (conj (::auction/bids auction) new-bid))]
    (concat
     ;; Put all the non-winning bids back in the hand
     (mapv (fn [bid]
             [:db/add (:db/id (::bid/hand bid)) ::hand/available-sun-disks (::bid/sun-disk bid)])
           other-bids)
     ;; Remove the auction from the epoch (it's done)
     ;; TODO have flag "in auction" so frontend can show last bid that was played
     [[:db/retract (:db/id epoch) ::epoch/auction (:db/id auction)]]
     (if winning-bid
       (concat
        ;; Move auction track tiles to winning hand
        (auction-tiles->hand-tx epoch (::bid/hand winning-bid))
        [;; Move the epoch's sun-disk into the winning hand as a used disk
         [:db/add (:db/id (::bid/hand winning-bid)) ::hand/used-sun-disks (::epoch/current-sun-disk epoch)]
         ;; Set the current epoch's sun disk (middle of the board) to the
         ;; winning disk
         [:db/add (:db/id epoch) ::epoch/current-sun-disk (::bid/sun-disk winning-bid)]]
        ;; If the player picks up a disaster tile, go into disaster resolution mode
        (when (some ::tile/disaster? (::epoch/auction-tiles epoch))
          [[:db/add (:db/id epoch) ::epoch/in-disaster? true]]))
       ;;TODO what if there is no winning bid, but the track isn't full?

       ;; If there isn't a winning bid, and the auction track is full, then
       ;; remove all tiles from the auction track
       (when (::auction/tiles-full? auction)
         [(discard-auction-tiles-op epoch)])))))

;; TODO when all players use up tiles, trigger end of epoch
(defn bid-tx
  "Moves the sun-disk from the hand to the middle of the board and triggers an end
  to the auction if it's the last bid"
  [hand sun-disk]
  (let [epoch (hand->epoch hand)]
    (concat
     ;; New bid. sun-disk moves from hand to middle
     (play-bid-tx {:hand     hand
                   :sun-disk sun-disk
                   :auction  (::epoch/auction epoch)})
     ;; Set the current hand
     (rotate-current-hand-tx epoch hand)
     (when (waiting-on-last-bid? (::epoch/auction epoch))
       ;; If the next bid would be the winner, then trigger
       ;; an end to the auction
       (let [new-bid {::bid/hand     hand
                      ::bid/sun-disk sun-disk}]
         (last-bid-tx {:auction (::epoch/auction epoch)
                       :new-bid new-bid}))))))

(pc/defmutation bid
  [{:keys [::db/conn]} {:keys [sun-disk] :as input}]
  {::pc/params    #{::hand/id :sun-disk}
   ::pc/transform notify-clients
   ::pc/output    [::game/id]}
  (let [hand  (d/entity @conn [::hand/id (::hand/id input)])
        epoch (hand->epoch hand)]
    (when-not (::epoch/auction epoch)
      (throw (ex-info {} "Not in an auction")))
    (d/transact! conn (bid-tx hand sun-disk))
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

    ;; TODO handle nile vs flood
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
                              [[:db/add (:db/id epoch) ::epoch/in-disaster? false]]))
    {::game/id (::game/id (hand->game hand))}))

(pc/defmutation use-god-tile
  [{:keys [::db/conn]} input]
  {::pc/params    #{:god-tile-id :auction-track-tile-id}
   ::pc/transform notify-clients
   ::pc/output    [::game/id]}
  (let [god-tile (d/entity @conn [::tile/id (:god-tile-id input)])
        hand (tile->hand god-tile)
        auction-track-tile (d/entity @conn [::tile/id (:auction-track-tile-id input)])
        epoch (hand->epoch hand)]
    (d/transact! conn (concat
                       [[:db/retract (:db/id hand) ::hand/tiles (:db/id god-tile)]]
                       (move-tile-tx auction-track-tile [epoch ::epoch/auction-tiles] [hand ::hand/tiles])))
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
