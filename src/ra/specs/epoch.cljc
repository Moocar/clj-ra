(ns ra.specs.epoch
  (:require [clojure.spec.alpha :as s]
            [ra.specs.player :as player]
            [ra.specs.tile :as tile]
            [ra.specs :as rs]))

(s/def ::current-sun-disk rs/sun-disk)
(s/def ::number #{1 2 3})
(s/def ::ra-tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::auction-tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::last-ra-invokee (s/keys :req [::player/id]))
(s/def ::player-hands (s/coll-of (s/keys :req [::player/id])))
