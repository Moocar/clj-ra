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
  ;; Turn on verbose logging
  (alter-var-root #'ra.log/*verbose* (constantly true))

  (require 'ra.model.game-test)
  (require 'clojure.test)

  ;; run all tests
  (clojure.test/run-all-tests #"ra.*-test")

  ;; Run a single test:
  (ra.model.game-test/use-up-last-tile-but-still-go)

  (require 'ra.instrument)
  (require 'datascript.core)

  ;; Run a playboox
  (ra.instrument/run (s) "ABDS" [])

  ;; Get the current game db
  (datascript.core/entity @(:ra.db/conn (s)) [:ra.specs.game/short-id "HRAY"])

  )
