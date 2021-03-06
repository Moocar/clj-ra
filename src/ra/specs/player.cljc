(ns ra.specs.player
  (:require [clojure.spec.alpha :as s]
            [ra.specs.tile :as tile]
            [ra.specs :as rs]))

(s/def ::tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::sun-disks (s/coll-of rs/sun-disk))
(s/def ::id nat-int?)
