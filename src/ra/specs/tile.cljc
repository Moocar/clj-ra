(ns ra.specs.tile
  (:require [clojure.spec.alpha :as s]
            [ra.specs :as rs]
            [ra.specs.tile.monument :as monument]
            [ra.specs.tile.civilization :as civilization]
            [ra.specs.tile.type :as tile-type]
            [ra.specs.tile.river :as river]))

(s/def ::id nat-int?)
(s/def ::type tile-type/types)
(s/def ::monument-type monument/types)
(s/def ::civilization-type civilization/types)
(s/def ::title rs/non-empty-string?)
(s/def ::disaster? boolean?)
(s/def ::scarab? boolean?)

(defn disaster? [t]
  (::disaster? t))

(defn type= [tile type]
  (= type (::type tile)))

(defn god? [t]
  (type= t ::tile-type/god))

(defn gold? [t]
  (type= t ::tile-type/gold))

(defn civ? [t]
  (and (type= t ::tile-type/civilization)
       (not (disaster? t))))

(defn pharoah? [t]
  (and (type= t ::tile-type/pharoah)
       (not (disaster? t))))

(defn monument? [t]
  (and (type= t ::tile-type/monument)
       (not (disaster? t))))

(defn ra? [t]
  (type= t ::tile-type/ra))

(defn river? [t]
  (and (type= t ::tile-type/river)
       (not (disaster? t))))

(defn nile? [t]
  (and (= (::river-type t) ::river/nile)
       (not (disaster? t))))

(defn flood? [t]
  (= (::river-type t) ::river/flood))

(defn drought? [t]
  (and (type= t ::tile-type/river)
       (disaster? t)))
