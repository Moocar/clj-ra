(ns ra.specs.hand
  (:require [clojure.spec.alpha :as s]
            [ra.specs :as rs]
            [ra.specs.tile :as tile]
            [ra.specs.player :as player]))

(s/def ::tiles (s/coll-of (s/keys :req [::tile/id])))
(s/def ::sun-disks (s/coll-of rs/sun-disk))
(s/def ::player (s/keys :req [::player/id]))
(s/def ::seat nat-int?)

(defn all-sun-disks [hand]
  (set
   (concat (::used-sun-disks hand)
           (::available-sun-disks hand))))

(defn highest-sun-disk [hand]
  (last (sort (all-sun-disks hand))))
