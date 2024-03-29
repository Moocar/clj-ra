(ns ra.model.tile
  (:require [ra.specs.tile :as tile]
            [ra.specs.tile.civilization :as civilization]
            [ra.specs.tile.monument :as monument]
            [ra.specs.tile.river :as river]
            [ra.specs.tile.type :as tile-type]))

(def q
  [::tile/id
   ::tile/type
   ::tile/title
   ::tile/scarab?
   ::tile/disaster?
   ::tile/civilization-type
   ::tile/monument-type
   ::tile/river-type])

(def tile-specs
  {{::tile/type ::tile-type/ra
    ::tile/title "Ra"} 30

   {::tile/type ::tile-type/god
    ::tile/title "God"} 8

   {::tile/type ::tile-type/civilization
    ::tile/civilization-type ::civilization/astronomy
    ::tile/title "Astronomy"} 5
   {::tile/type ::tile-type/civilization
    ::tile/civilization-type ::civilization/agriculture
    ::tile/title "Agriculture"} 5
   {::tile/type ::tile-type/civilization
    ::tile/civilization-type ::civilization/writing
    ::tile/title "Writing"} 5
   {::tile/type ::tile-type/civilization
    ::tile/civilization-type ::civilization/religion
    ::tile/title "Religion"} 5
   {::tile/type ::tile-type/civilization
    ::tile/civilization-type ::civilization/art
    ::tile/title "Art"} 5
   {::tile/type ::tile-type/civilization
    ::tile/disaster? true
    ::tile/title "War"} 4

   {::tile/type ::tile-type/monument
    ::tile/monument-type ::monument/fortress
    ::tile/scarab? true
    ::tile/title "Fortress"} 5
   {::tile/type ::tile-type/monument
    ::tile/monument-type ::monument/obelisk
    ::tile/scarab? true
    ::tile/title "Obelisk"} 5
   {::tile/type ::tile-type/monument
    ::tile/monument-type ::monument/palace
    ::tile/scarab? true
    ::tile/title "Palace"} 5
   {::tile/type ::tile-type/monument
    ::tile/monument-type ::monument/pyramid
    ::tile/scarab? true
    ::tile/title "Pyramid"} 5
   {::tile/type ::tile-type/monument
    ::tile/monument-type ::monument/temple
    ::tile/scarab? true
    ::tile/title "Temple"} 5
   {::tile/type ::tile-type/monument
    ::tile/monument-type ::monument/statue
    ::tile/scarab? true
    ::tile/title "Statue"} 5
   {::tile/type ::tile-type/monument
    ::tile/monument-type ::monument/step-pyramid
    ::tile/scarab? true
    ::tile/title "Step Pyramid"} 5
   {::tile/type ::tile-type/monument
    ::tile/monument-type ::monument/sphinx
    ::tile/scarab? true
    ::tile/title "Sphinx"} 5
   {::tile/type ::tile-type/monument
    ::tile/disaster? true
    ::tile/title "Earthquake"} 5

   {::tile/type ::tile-type/gold
    ::tile/title "Gold"} 5

   {::tile/type ::tile-type/pharoah
    ::tile/scarab? true
    ::tile/title "Pharoah"} 25
   {::tile/type ::tile-type/pharoah
    ::tile/title "Funeral"
    ::tile/disaster? true} 2

   {::tile/type ::tile-type/river
    ::tile/scarab? true
    ::tile/title "Nile"
    ::tile/river-type ::river/nile} 25
   {::tile/type ::tile-type/river
    ::tile/title "Flood"
    ::tile/river-type ::river/flood} 12
   {::tile/type ::tile-type/river
    ::tile/title "Drought"
    ::tile/disaster? true} 2
   })

(defn new-bag []
  (let [id (atom 0)]
    (->> tile-specs
         (reduce-kv (fn [coll tile num]
                      (concat coll (map #(assoc tile ::tile/id %)
                                        (repeatedly num #(swap! id inc)))))
                    [])
         shuffle)))
