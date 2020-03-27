(ns ra.specs.epoch
  (:require [clojure.spec.alpha :as s]
            [ra.specs.sun-disk :as sun-disk]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]))

(s/def ::current-sun-disk (s/keys :req [::sun-disk/number]))
(s/def ::number #{1 2 3})
(s/def ::ra-tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::auction-tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::last-ra-invokee (s/keys :req [::player/id]))
(s/def ::player-hands (s/coll-of (s/keys :req [::player/id])))
