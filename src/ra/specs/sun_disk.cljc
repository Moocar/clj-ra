(ns ra.specs.sun-disk
  (:require [clojure.spec.alpha :as s]))

(s/def ::number pos-int?)
(s/def ::used? boolean?)
