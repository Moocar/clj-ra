(ns ra.specs.game
  (:require [clojure.spec.alpha :as s]
            [ra.specs.epoch :as epoch]
            [ra.specs.player :as player]))

(s/def ::id nat-int?)
(s/def ::current-epoch (s/keys :req [::epoch/number]))
(s/def ::players (s/coll-of (s/keys :req [::player/id])))
