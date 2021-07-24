(ns user
  (:require [integrant.repl :refer [clear go halt prep init reset-all]]
            [hashp.core]
            [clojure.tools.namespace.repl :as repl]
            ra.integrant))

(repl/disable-reload! (find-ns 'user))

(repl/set-refresh-dirs "src")

(defn reset []
  (integrant.repl/reset)
  :ok)

(integrant.repl/set-prep! ra.integrant/prep)

(defn s [] integrant.repl.state/system)

(defn c [] integrant.repl.state/config)
