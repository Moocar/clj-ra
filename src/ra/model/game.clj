(ns ra.model.game
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [com.fulcrologic.fulcro.networking.websocket-protocols
             :as
             fws-protocols]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [datascript.core :as d]
            [integrant.core :as ig]
            [ra.core :as core :refer [update-when]]
            [ra.date :as date]
            [ra.db :as db]
            [ra.model.score :as m-score]
            [ra.model.tile :as m-tile]
            [ra.specs.auction :as auction]
            [ra.specs.auction.bid :as bid]
            [ra.specs.auction.reason :as auction-reason]
            [ra.specs.game :as game]
            [ra.specs.game.event :as event]
            [ra.specs.game.event.type :as event-type]
            [ra.specs.hand :as hand]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.specs.tile.type :as tile-type]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Small helpers

(defn hand-turn? [game hand]
  (= (game/current-hand game) hand))

(defn check-current-hand [game hand]
  (when-not (hand-turn? game hand)
    (throw (ex-info "It's not your turn"
                    {:current-hand (::hand/seat (game/current-hand game))
                     :tried-hand   (::hand/seat hand)}))))

(defn move-thing-tx
  "Moves something from from to to"
  [thing from to]
  (let [[from-entity from-attr] from
        [to-entity to-attr] to]
    [[:db/retract (:db/id from-entity) from-attr thing]
     [:db/add (:db/id to-entity) to-attr thing]]))

(defn load-player [db player-id]
  (d/entity db [::player/id player-id]))

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

(defn load-game [db query game-id]
  (let [game (d/pull db query [::game/id game-id])]
    (-> game
        ;; TODO add zdt encoding to websocket transit
        (update-when ::game/started-at str)
        (update-when ::game/finished-at str)
        (update ::game/auction-tiles #(vec (sort-by :db/id %)))
        (update ::game/hands
                (fn [hands]
                  (mapv (fn [hand]
                          (-> hand
                              (assoc ::hand/my-go? (= (::hand/seat hand)
                                                      (::hand/seat (::game/current-hand game))))))
                        hands))))))

(def player-q
  [::player/name
   ::player/id])

(def auction-q
  [::auction/reason
   {::auction/ra-hand [::hand/id]}
   ::auction/tiles-full?
   {::auction/bids [{::bid/hand [::hand/id {::hand/player [::player/name]}]}
                    ::bid/sun-disk]}])

(def tile-q
  [::tile/id
   ::tile/title
   ::tile/disaster?
   ::tile/scarab?
   ::tile/type
   ::tile/auction-track-position])

(def hand-q
  [::hand/available-sun-disks
   ::hand/used-sun-disks
   ::hand/my-go?
   ::hand/seat
   ::hand/score
   ::hand/id
   {::hand/tiles tile-q}
   {::hand/player player-q}])

(def event-q
  [::event/id
   ::event/type
   ::event/data])

(def game-q
  [::game/current-sun-disk
   ::game/epoch
   ::game/finished-at
   ::game/id
   ::game/in-disaster?
   ::game/short-id
   ::game/started-at
   {::game/auction auction-q}
   {::game/auction-tiles tile-q}
   {::game/current-hand hand-q}
   {::game/events event-q}
   {::game/hands hand-q}
   {::game/last-ra-invoker [::hand/id]}
   {::game/players player-q}
   {::game/ra-tiles tile-q}])


(pc/defresolver game-resolver [{:keys [::db/conn ::p/parent-query]}
                               {:keys [::game/id]}]
  {::pc/input  #{::game/id}
   ::pc/output game-q}
  (load-game @conn parent-query id))

(pc/defresolver short-game-resolver [{:keys [::db/conn]}
                               {:keys [::game/short-id]}]
  {::pc/input #{::game/short-id}
   ::pc/output [::game/id]}
  (try
    (let [result (d/pull @conn [::game/id] [::game/short-id (str/upper-case short-id)])]
      result)
    (catch Exception _
      nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Notify

(defn notify-clients [websockets cids game]
  (doseq [o cids]
    (fws-protocols/push websockets o :refresh game)))

(defn notify-other-clients! [env game]
  (when (:connected-uids env)
    (let [other-cids (disj (:any @(:connected-uids env)) (:cid env))]
      (notify-clients (:websockets env) other-cids game))))

(defn notify-other-clients-transform [{:keys [::pc/mutate] :as mutation}]
  (assoc mutation
         ::pc/mutate
         (fn [{:keys [::db/conn] :as env} params]
           (let [result (mutate env params)]
             (when (:connected-uids env)
               (when-let [game-id (::game/id result)]
                 (let [game (load-game @conn game-q game-id)]
                   (notify-other-clients! env game))))
             result))))

;; full env
(defn notify-all-clients! [env game-id]
  (let [websockets (:ra.server/websockets env)
        connected-uids (:any @(:connected-uids (:websockets websockets)))
        websockets (:websockets websockets)
        cids connected-uids]
    (notify-clients websockets cids (load-game @(::db/conn env) game-q game-id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Txs

(defn event-tx [game event-type data]
  (let [evt-id -1]
    [(merge
      {:db/id     evt-id
       ::event/id (ra.db/uuid)
       ::event/type event-type
       ::event/data data})
     [:db/add (:db/id game) ::game/events evt-id]]))

(defn next-hand
  "Returns the hand to the left of the given hand that has one or more
  sun disks left"
  [game current-hand]
  (assert current-hand)
  (->> (::game/hands game)
       (sort-by ::hand/seat)
       (repeat 2)
       (apply concat)
       (drop-while (fn [hand] (not= hand current-hand)))
       (rest)
       (take (game/player-count game))
       (filter (fn [hand] (seq (::hand/available-sun-disks hand))))
       (first)))

(defn rotate-current-hand-tx
  "Sets the game's current hand to the player to the left of the given
  hand that has one or more sun disks left"
  [game hand]
  (assert hand)
  [[:db/add (:db/id game) ::game/current-hand (:db/id (next-hand game hand))]])

(defn start-auction-tx
  "Create an auction and adds it to the game"
  [{:keys [hand auction-reason game]}]
  (let [auction-id -1]
    [[:db/add auction-id ::auction/ra-hand (:db/id hand)]
     [:db/add auction-id ::auction/reason auction-reason]
     [:db/add auction-id ::auction/tiles-full? (game/auction-tiles-full? game)]
     [:db/add (:db/id game) ::game/last-ra-invoker (:db/id hand)]
     [:db/add (:db/id game) ::game/auction auction-id]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create/Join/Start game mutations

(pc/defmutation new-game [{:keys [::db/conn]} _]
  {::pc/params []
   ::pc/output [::game/id]}
  (let [tile-bag (find-all-tiles @conn)
        entity   {::game/id       (db/uuid)
                  ::game/short-id (game/new-short-id)
                  ::game/tile-bag tile-bag}]
    (d/transact! conn [entity])
    entity))

(pc/defmutation join-game [{:keys [::db/conn]}
                           {game-id   ::game/id
                            player-id ::player/id}]
  {::pc/params    [::game/id ::player/id]
   ::pc/transform notify-other-clients-transform
   ::pc/output    [::game/id]}
  (let [game   (d/entity @conn [::game/id game-id])
        player (d/entity @conn [::player/id player-id])]
    (when (::game/started-at game)
      (throw (ex-info "Game already started" {})))
    (when (some #{player} (::game/players game))
      (throw (ex-info "You've already joined this game" {})))
    (if (>= (game/player-count game) 5)
      (throw (ex-info "Maximum players already reached" {}))
      (d/transact! conn
                   (concat
                    [[:db/add [::game/id game-id] ::game/players [::player/id player-id]]]
                    (event-tx game ::event-type/join-game {:player {::player/id player-id}}))
                   {::game/id game-id})))
  {::game/id game-id})

(pc/defmutation leave-game [{:keys [::db/conn]}
                           {game-id ::game/id player-id ::player/id}]
  {::pc/params [::game/id ::player/id]
   ::pc/transform notify-other-clients-transform
   ::pc/output [::game/id]}
  (let [game (d/entity @conn [::game/id game-id])]
    (when (::game/started-at game)
      (throw (ex-info "Can't leave game that has already started" {})))
    (d/transact! conn
                 (concat
                  [[:db/retract [::game/id game-id] ::game/players [::player/id player-id]]]
                  (event-tx game ::event-type/leave-game {:player {::player/id player-id}}))
                 {::game/id game-id}))
  {::game/id game-id})

(defn do-start-game [conn game-id]
  (let [game (d/entity @conn [::game/id game-id])]
    (if-let [started-at (::game/started-at game)]
      (throw (ex-info "Game already started" {:started-at started-at}))
      (let [num-players (game/player-count game)]
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
                                  (shuffle (get game/sun-disk-sets num-players))
                                  (repeatedly #(swap! id-atom dec))
                                  (range))]
            (d/transact! conn
                         (concat
                          player-hands
                          (let [gid [::game/id game-id]]
                            [{:db/id                  gid
                              ::game/started-at       (date/zdt)
                              ::game/epoch            1
                              ::game/current-sun-disk 1
                              ::game/current-hand     (:db/id (last (sort-by hand/highest-sun-disk player-hands)))
                              ::game/hands            (map :db/id player-hands)}])
                          (event-tx game ::event-type/game-started {}))
                         {::game/id game-id})))))))

(pc/defmutation start-game [{:keys [::db/conn]} {game-id ::game/id}]
  {::pc/params [::game/id]
   ::pc/transform notify-other-clients-transform
   ::pc/output [::game/id]}
  (do-start-game conn game-id)
  {::game/id game-id})

(defn do-clear-game [conn game-id]
  (assert game-id)
  (let [ident [::game/id game-id]]
    (d/transact! conn
                 (concat
                  [[:db/retract ident ::game/started-at]
                   [:db/retract ident ::game/tile-bag]
                   [:db/retract ident ::game/epoch]
                   [:db/retract ident ::game/current-sun-disk]
                   [:db/retract ident ::game/events]
                   [:db/retract ident ::game/hands]
                   [:db/retract ident ::game/current-hand]]
                  (let [tiles (find-all-tiles @conn)]
                    (mapv (fn [tile-id]
                            [:db/add ident ::game/tile-bag tile-id])
                          tiles)))
                 {::game/id game-id})))

(pc/defmutation reset [{:keys [::db/conn]} {game-id ::game/id}]
  {::pc/params [::game/id]
   ::pc/transform notify-other-clients-transform
   ::pc/output [::game/id]}
  (do-clear-game conn game-id)
  (do-start-game conn game-id)
  {::game/id game-id})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Invoke Ra

(pc/defmutation invoke-ra [{:keys [::db/conn]} input]
  {::pc/params #{::hand/id ::game/id}
   ::pc/transform notify-other-clients-transform
   ::pc/output [::game/id]}
  (assert (::hand/id input))
  (let [hand  (d/entity @conn [::hand/id (::hand/id input)])
        game  (d/entity @conn [::game/id (::game/id input)])]
    (println [(::hand/seat hand) ::event-type/invoke-ra])
    (check-current-hand game hand)
    (d/transact! conn
                 (concat
                  (start-auction-tx {:hand           hand
                                     :auction-reason ::auction-reason/invoke
                                     :game          game})
                  (rotate-current-hand-tx game hand)
                  (event-tx game
                            ::event-type/invoke-ra
                            {:hand {::hand/id (::hand/id hand)}}))
                 (select-keys input [::game/id]))
    (select-keys input [::game/id])))

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

(defn finish-epoch-tx [game]
  (let [hand-scores  (m-score/score-epoch game)]
    (assert (< (::game/epoch game) 4))
    (concat
     ;; Calculate and store score on each hand
     (mapv (fn [hand hand-scores]
             [:db/add (:db/id hand) ::hand/score (+ (::hand/score hand)
                                                    (m-score/tally-hand hand-scores))])
           (::game/hands game)
           hand-scores)

     (if (= (::game/epoch game) 3)

       ;; End of the game
       [[:db/add (:db/id game) ::game/finished-at (date/zdt)]]

       ;; Normal epoch
       (concat
        (mapcat flip-sun-disks-tx (::game/hands game))
        (mapcat discard-non-scarabs-tx (::game/hands game))
        [[:db/add (:db/id game) ::game/epoch (inc (::game/epoch game))]
         [:db/retract (:db/id game) ::game/last-ra-invoker]
         [:db/retract (:db/id game) ::game/auction]
         [:db/retract (:db/id game) ::game/in-disaster?]
         [:db/retract (:db/id game) ::game/ra-tiles]
         [:db/retract (:db/id game) ::game/auction-tiles]]
        ;; The first hand of the next epoch is the one with the highest sun disk
        (let [leading-hand (game/hand-with-highest-sun-disk game)]
          [[:db/add (:db/id game) ::game/current-hand (:db/id leading-hand)]])))
     (event-tx game
               ::event-type/finish-epoch
               {:hand-scores (map (fn [hand-score]
                                    (select-keys hand-score [::hand/id :tile-scores :sun-disks]))
                                  hand-scores)}))))

(defn do-draw-tile [{:keys [::db/conn] :as env} game hand tile]
  (check-current-hand game hand)
  (when (game/auction-tiles-full? game)
    (throw (ex-info "Auction Track full" {})))
  (when (::game/in-disaster? game)
    (throw (ex-info "Waiting for players to discard disaster tiles" {})))

  (let [tx    (concat
               (if (tile/ra? tile)
                 (concat
                  (move-thing-tx (:db/id tile) [game ::game/tile-bag] [game ::game/ra-tiles])
                  (start-auction-tx {:hand           hand
                                     :auction-reason ::auction-reason/draw
                                     :game          game}))
                 (let [last-tile (last (sort-by ::tile/auction-track-position (::game/auction-tiles game)))]
                   (concat
                    (move-thing-tx (:db/id tile) [game ::game/tile-bag] [game ::game/auction-tiles])
                    [[:db/add (:db/id game) ::game/current-hand (:db/id (next-hand game hand))]
                     [:db/add (:db/id tile) ::tile/auction-track-position (inc (or (::tile/auction-track-position last-tile) 0))]])))
               (event-tx game
                         ::event-type/draw-tile
                         {:hand {::hand/id (::hand/id hand)}
                          :tile (select-keys tile tile-q)}))]
    (d/transact! conn tx {::game/id (::game/id game)})
    (notify-other-clients! env (load-game @conn game-q (::game/id game)))
    (when (tile/ra? tile)
      (let [tx (if (game/last-ra? game)
                 ;; finish epoch
                 (finish-epoch-tx game)
                 ;; normal ra tile
                 (concat
                  [[:db/add (:db/id game) ::game/in-auction? true]]
                  (rotate-current-hand-tx game hand)))]
        (d/transact! conn tx {::game/id (::game/id game)})
        (notify-other-clients! env (load-game @conn game-q (::game/id game)))))))

(pc/defmutation draw-tile [{:keys [::db/conn] :as env} input]
  {::pc/params [::hand/id ::game/id]
   ::pc/output [::game/id]}
  (let [hand (d/entity @conn [::hand/id (::hand/id input)])
        game (d/entity @conn [::game/id (::game/id input)])
        tile (d/entity @conn (sample-tile @conn game))]
    (println [(::hand/seat hand) ::event-type/draw-tile])
    (do-draw-tile env game hand tile)
    (select-keys input [::game/id])))

(defn waiting-on-last-bid?
  "Returns true if the current bid auction's bid is the last"
  [game auction]
  (let [active-hands (filter (fn [hand]
                               (or (seq (::hand/available-sun-disks hand))
                                   (some #(= hand %) (map ::bid/hand (::auction/bids auction)))))
                             (::game/hands game))]
    (= (inc (count (::auction/bids auction)))
       (count active-hands))))

(defn calc-winning-bid
  "Given an auction and a potential new bid, return the winning bid (which could
  be the new bid, or an existing one in the auction"
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
  [game hand]
  (concat
   ;; Clear the auction track
   [[:db/retract (:db/id game) ::game/auction-tiles]]
   ;; Move all tiles in the auction track to the given hand
   (mapv (fn [tile]
           [:db/add (:db/id hand) ::hand/tiles (:db/id tile)])
         (::game/auction-tiles game))))

(defn sun-disks-in-play? [game]
  (some (fn [hand]
            (seq (::hand/available-sun-disks hand)))
        (::game/hands game)))

(defn last-bid-tx [game auction new-bid winning-bid]
  (let [other-bids (filter #(not-winning-bid % winning-bid)
                           (conj (::auction/bids auction) new-bid))]
    (concat
     ;; Put all the non-winning bids back in the hand
     (mapv (fn [bid]
             [:db/add (:db/id (::bid/hand bid)) ::hand/available-sun-disks (::bid/sun-disk bid)])
           other-bids)

     ;; Remove the auction from the game (it's done)
     ;; TODO have flag "in auction" so frontend can show last bid that was played
     [[:db/retract (:db/id game) ::game/auction (:db/id auction)]]

     (if winning-bid
       ;; If there was a winning bid (not everyone passed
       (concat
        ;; Move auction track tiles to winning hand
        (auction-tiles->hand-tx game (::bid/hand winning-bid))
        [ ;; Move the game's sun-disk into the winning hand as a used disk
         [:db/add (:db/id (::bid/hand winning-bid)) ::hand/used-sun-disks (::game/current-sun-disk game)]
         ;; Set the current epoch's sun disk (middle of the board) to the
         ;; winning disk
         [:db/add (:db/id game) ::game/current-sun-disk (::bid/sun-disk winning-bid)]]
        ;; If the player picks up a disaster tile, go into disaster resolution mode
        (event-tx game
                  ::event-type/bid
                  {:hand        {::hand/id (::hand/id (::bid/hand new-bid))}
                   :sun-disk    (::bid/sun-disk new-bid)
                   :last?       true
                   :winning-bid {::bid/hand {::hand/id (::hand/id (::bid/hand winning-bid))}
                                 ::bid/sun-disk (::bid/sun-disk winning-bid)}
                   :tiles-won (map #(select-keys % tile-q) (::game/auction-tiles game))})
        (when (some ::tile/disaster? (::game/auction-tiles game))
          [[:db/add (:db/id game) ::game/in-disaster? true]])

        (let [the-next-hand (if (some ::tile/disaster? (::game/auction-tiles game))
                              (::bid/hand winning-bid)
                              (next-hand game (::game/last-ra-invoker game)))]
          [[:db/add (:db/id game) ::game/current-hand (:db/id the-next-hand)]]))

       ;; TODO If they win a disaster tile, but they have no choices, then
       ;; discard for them and move to the next hand

       ;; If everyone passed
       (concat
        [[:db/add (:db/id game) ::game/current-hand (:db/id (next-hand game (::game/last-ra-invoker game)))]]

        (event-tx game
                  ::event-type/bid
                  {:hand        {::hand/id (::hand/id (::bid/hand new-bid))}
                   :sun-disk    (::bid/sun-disk new-bid)
                   :last?       true})
        ;; If everyone passed
        (if (::auction/tiles-full? auction)
          ;; If the auction track is full, then we discard all the tiles
          [[:db/retract (:db/id game) ::game/auction-tiles]]
          ;; If the auction track isn't full, and everyone else passed, you have to bid
          (when (= ::auction-reason/invoke (::auction/reason auction))
            (throw (ex-info "You voluntarily invoked ra. You must bid" {})))))))))

(pc/defmutation bid
  [{:keys [::db/conn] :as env} {:keys [sun-disk] :as input}]
  {::pc/params    #{::hand/id ::game/id :sun-disk}
   ::pc/output    [::game/id]}
  (assert (contains? input :sun-disk))
  (assert (::hand/id input))
  (let [hand  (d/entity @conn [::hand/id (::hand/id input)])
        game (d/entity @conn [::game/id (::game/id input)])
        auction (::game/auction game)
        new-bid {::bid/hand     hand
                 ::bid/sun-disk sun-disk}
        winning-bid (calc-winning-bid auction new-bid)]
    (println [(::hand/seat hand) ::event-type/bid sun-disk])
    (check-current-hand game hand)
    (when (= (count (::auction/bids auction))
             (game/player-count game))
      (throw (ex-info "Auction finished" {})))
    (assert (= hand (::game/current-hand game)))
    (when-not (::game/auction game)
      (throw (ex-info "Not in an auction" {})))

    (let [tx (let [bid-id -1]
               (concat
                [[:db/add (:db/id auction) ::auction/bids bid-id]
                 [:db/add bid-id ::bid/hand (:db/id hand)]]
                (when sun-disk
                  (move-thing-tx sun-disk
                                 [hand ::hand/available-sun-disks]
                                 [{:db/id bid-id} ::bid/sun-disk]))
                (when-not (waiting-on-last-bid? game auction)
                  [[:db/add (:db/id game) ::game/current-hand (:db/id (next-hand game hand))]])
                (event-tx game
                          ::event-type/bid
                          {:hand {::hand/id (::hand/id hand)}
                           :sun-disk sun-disk})))]
      (d/transact! conn tx
                   (select-keys input [::game/id]))
      (notify-other-clients! env (load-game @conn game-q (::game/id game)))
      (when (waiting-on-last-bid? game auction)
        ;; Perform a fake tx to find out if we have run out of sun disks. And if so,
        ;; finish the epoch
        (let [tx (last-bid-tx game auction new-bid winning-bid)
              db-after (:db-after (d/with @conn tx))
              new-game (d/entity db-after (:db/id game))
              tx (if (sun-disks-in-play? new-game)
                   tx
                   (concat tx (finish-epoch-tx new-game)))]
          (d/transact! conn tx (select-keys input [::game/id]))
          (notify-other-clients! env (load-game @conn game-q (::game/id game)))))
      (select-keys input [::game/id]))))

(defn discard-tile-op [hand tile]
  [:db/retract (:db/id hand) ::hand/tiles (:db/id tile)])

(pc/defmutation discard-disaster-tiles
  [{:keys [::db/conn]} input]
  {::pc/params    #{::hand/id ::game/id :tile-ids}
   ::pc/transform notify-other-clients-transform
   ::pc/output    [::game/id]}
  (let [hand                 (d/entity @conn [::hand/id (::hand/id input)])
        game                 (d/entity @conn [::game/id (::game/id input)])
        disaster-tiles       (hand/disaster-tiles hand)
        selected-tiles       (set (map (fn [tile-id] (d/entity @conn [::tile/id tile-id])) (:tile-ids input)))]
    (check-current-hand game hand)
    (when-not (seq disaster-tiles)
      (throw (ex-info "No disaster tiles in hand" {})))

    (hand/check-selected-disaster-tiles hand selected-tiles)

    (d/transact! conn
                 (concat (mapv #(discard-tile-op hand %)
                               (set/union selected-tiles disaster-tiles))
                         [[:db/add (:db/id game) ::game/in-disaster? false]
                          [:db/add (:db/id game) ::game/current-hand (:db/id (next-hand game (::game/last-ra-invoker game)))]]
                         (event-tx game
                                   ::event-type/discard-disaster-tiles
                                   {:hand {::hand/id (::hand/id hand)}}))
                 (select-keys input [::game/id]))
    (select-keys input [::game/id])))

(pc/defmutation use-god-tile
  [{:keys [::db/conn]} input]
  {::pc/params    #{::hand/id ::game/id :god-tile-id :auction-track-tile-id}
   ::pc/transform notify-other-clients-transform
   ::pc/output    [::game/id]}
  (let [db @conn
        god-tile (d/entity db [::tile/id (:god-tile-id input)])
        hand (d/entity db [::hand/id (::hand/id input)])
        auction-track-tile (d/entity @conn [::tile/id (:auction-track-tile-id input)])
        game (d/entity db [::game/id (::game/id input)])]
    (check-current-hand game hand)
    (when (= (::tile/type auction-track-tile) ::tile-type/god)
      (throw (ex-info "Can't use god tile on a god tile" {})))
    (d/transact! conn
                 (concat
                  [[:db/retract (:db/id hand) ::hand/tiles (:db/id god-tile)]]
                  (move-thing-tx (:db/id auction-track-tile) [game ::game/auction-tiles] [hand ::hand/tiles])
                  ;; TODO include the aquired tile in the event
                  (event-tx game
                            ::event-type/use-god-tile
                            {:hand {::hand/id (::hand/id hand)}
                             :tile (d/pull db tile-q (:db/id auction-track-tile))})
                  (rotate-current-hand-tx game hand))
                 (select-keys input [::game/id]))
    (select-keys input [::game/id])))

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
   leave-game
   new-game
   reset
   start-game
   use-god-tile

   game-resolver
   short-game-resolver])
