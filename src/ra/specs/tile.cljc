(ns ra.specs.tile
  (:require [clojure.spec.alpha :as s]
            [ra.specs :as rs]
            [ra.specs.tile.monument :as monument]
            [ra.specs.tile.civilization :as civilization]
            [ra.specs.tile.type :as tile-type]))

(s/def ::id nat-int?)
(s/def ::type tile-type/types)
(s/def ::monument-type monument/types)
(s/def ::civilization-type civilization/types)
(s/def ::title rs/non-empty-string?)
(s/def ::disaster? boolean?)
(s/def ::scarab? boolean?)
