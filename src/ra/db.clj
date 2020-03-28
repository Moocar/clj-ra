(ns ra.db
  (:require [datascript.core :as d]
            [integrant.core :as ig]))

(defn uuid []
  (java.util.UUID/randomUUID))

(def schema
  {
   :ra.specs.tile/id {:db/unique :db.unique/value}

   :ra.specs.player/id {:db/unique :db.unique/value}

   :ra.specs.hand/available-sun-disks {:db/cardinality :db.cardinality/many}
   :ra.specs.hand/used-sun-disks      {:db/cardinality :db.cardinality/many}
   :ra.specs.hand/player              {:db/valueType :db.type/ref}
   :ra.specs.hand/tiles               {:db/cardinality :db.cardinality/many
                                       :db/valueType   :db.type/ref}

   :ra.specs.epoch/hands         {:db/cardinality :db.cardinality/many
                                  :db/valueType   :db.type/ref}
   :ra.specs.epoch/auction-tiles {:db/cardinality :db.cardinality/many
                                  :db/valueType   :db.type/ref}
   :ra.specs.epoch/current-hand  {:db/valueType :db.type/ref}

   :ra.specs.game/id            {:db/unique :db.unique/value}
   :ra.specs.game/tile-bag      {:db/cardinality :db.cardinality/many
                                 :db/valueType   :db.type/ref}
   :ra.specs.game/players       {:db/cardinality :db.cardinality/many
                                 :db/valueType   :db.type/ref}
   :ra.specs.game/current-epoch {:db/valueType :db.type/ref}



   })

(defmethod ig/init-key ::conn [_ _]
  (d/create-conn schema))

(defmethod ig/suspend-key! ::conn [_ conn]
  conn)

(defmethod ig/resume-key ::conn [_ _ _ old-impl]
  old-impl)
