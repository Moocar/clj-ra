(ns ra.db
  (:require [datascript.core :as d]
            [integrant.core :as ig]))

(defn uuid []
  (java.util.UUID/randomUUID))

(def schema
  {:ra.specs.game/id {:db/unique :db.unique/value}})

(defmethod ig/init-key ::conn [_ _]
  (d/create-conn schema))
