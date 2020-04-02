(ns ra.core)

#?(:clj (defn uuid [s] (java.util.UUID/fromString s)))

(defn update-when
  "like update but only applies f if m contains k"
  [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))
