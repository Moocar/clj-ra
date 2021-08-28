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

(defn title= [t title]
  (= (::title t) title))

(defn ra? [t]
  (type= t ::tile-type/ra))

(defn god? [t]
  (type= t ::tile-type/god))

(defn gold? [t]
  (type= t ::tile-type/gold))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Civs

(defn civ? [t]
  (and (type= t ::tile-type/civilization)
       (not (disaster? t))))

(defn civ= [t civ-type]
  (= civ-type (::civilization-type t)))

(defn art? [t]
  (civ= t ::civilization/art))

(defn writing? [t]
  (civ= t ::civilization/writing))

(defn agriculture? [t]
  (civ= t ::civilization/agriculture))

(defn astronomy? [t]
  (civ= t ::civilization/astronomy))

(defn religion? [t]
  (civ= t ::civilization/religion))

(defn war? [t]
  (and (type= t ::tile-type/civilization)
       (disaster? t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pharoah

(defn pharoah? [t]
  (and (type= t ::tile-type/pharoah)
       (not (disaster? t))))

(defn funeral? [t]
  (and (type= t ::tile-type/pharoah)
       (disaster? t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rivers

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Monuments

(defn monument? [t]
  (and (type= t ::tile-type/monument)
       (not (disaster? t))))

(defn step-pyramid? [t]
  (= ::monument/step-pyramid (::monument-type t)))

(defn palace? [t]
  (= ::monument/palace (::monument-type t)))

(defn statue? [t]
  (= ::monument/statue (::monument-type t)))

(defn obelisk? [t]
  (= ::monument/obelisk (::monument-type t)))

(defn temple? [t]
  (= ::monument/temple (::monument-type t)))

(defn sphinx? [t]
  (= ::monument/sphinx (::monument-type t)))

(defn fortress? [t]
  (= ::monument/fortress (::monument-type t)))

(defn pyramid? [t]
  (= ::monument/fortress (::monument-type t)))

(defn earthquake? [t]
  (and (type= t ::tile-type/monument)
       (disaster? t)) )
