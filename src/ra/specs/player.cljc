(ns ra.specs.player
  (:require [clojure.spec.alpha :as s]
            [ra.specs.tile :as tile]
            [ra.specs.sun-disk :as sun-disk]))

(s/def ::tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::sun-disks (s/coll-of (s/keys :req [::sun-disk/number])))
(s/def ::id nat-int?)
