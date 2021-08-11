(ns ra.db
  (:require [datascript.core :as d]
            [integrant.core :as ig]))

(defn uuid []
  (java.util.UUID/randomUUID))

(def schema
  {
   :ra.specs.player/id {:db/unique :db.unique/value}

   :ra.specs.tile/id {:db/unique :db.unique/value}

   :ra.specs.hand/id                  {:db/unique :db.unique/value}
   :ra.specs.hand/available-sun-disks {:db/cardinality :db.cardinality/many}
   :ra.specs.hand/used-sun-disks      {:db/cardinality :db.cardinality/many}
   :ra.specs.hand/player              {:db/valueType :db.type/ref}
   :ra.specs.hand/tiles               {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}

   :ra.specs.auction.bid/hand {:db/valueType :db.type/ref}

   :ra.specs.auction/bids    {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}
   :ra.specs.auction/ra-hand {:db/valueType :db.type/ref}

   :ra.specs.event/id {:db/unique :db.unique/value}

   ;; :ra.specs.epoch-hand/epoch
   :ra.specs.epoch-hand/hand  {:db/valueType :db.type/ref}
   :ra.specs.epoch-hand/tiles {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}

   :ra.specs.game/auction         {:db/valueType :db.type/ref}
   :ra.specs.game/auction-tiles   {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}
   :ra.specs.game/current-hand    {:db/valueType :db.type/ref}
   :ra.specs.game/epoch-hands     {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}
   :ra.specs.game/events          {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}
   :ra.specs.game/hands           {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}
   :ra.specs.game/id              {:db/unique :db.unique/value}
   :ra.specs.game/last-ra-invoker {:db/valueType :db.type/ref}
   :ra.specs.game/players         {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}
   :ra.specs.game/ra-tiles        {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}
   :ra.specs.game/short-id        {:db/unique :db.unique/value}
   :ra.specs.game/tile-bag        {:db/cardinality :db.cardinality/many :db/valueType :db.type/ref}
   })

(defmethod ig/init-key ::conn [_ _]
  (let [conn (d/create-conn schema)]
    conn))

(defmethod ig/suspend-key! ::conn [_ conn]
  conn)

(defmethod ig/resume-key ::conn [_ _ _ old-impl]
  old-impl)
