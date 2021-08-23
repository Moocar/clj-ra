(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            integrant.repl
            integrant.repl.state
            ra.log
            ra.integrant))

(repl/disable-reload! (find-ns 'user))

(repl/set-refresh-dirs "src" "test")

(defn reset []
  (integrant.repl/reset)
  (alter-var-root #'ra.log/*verbose* (constantly true))
  :ok)

(integrant.repl/set-prep! ra.integrant/prep)

(defn s [] integrant.repl.state/system)

(defn c [] integrant.repl.state/config)

(comment
  (require 'clojure.test)
  (clojure.test/run-all-tests #"ra.*-test")

  (require 'ra.instrument)
  (require 'datascript.core)
  (ra.instrument/run (s) "ABDS" [])
  (datascript.core/entity @(:ra.db/conn (s)) [:ra.specs.game/short-id "HRAY"]))
