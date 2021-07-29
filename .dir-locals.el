((clojure-mode
  (cider-clojure-cli-global-options . "-A:server-dev")
  ;;(eval . (cider-register-cljs-repl-type 'super-cljs "(do (foo) (bar))"))
  ;;(cider-default-cljs-repl . super-cljs)
  (cider-ns-refresh-before-fn . "integrant.repl/suspend")
  (cider-ns-refresh-after-fn . "integrant.repl/resume")

  (eval . (put-clojure-indent '>defn 2))
  (cider-save-file-on-load . t)
  (cider-filter-regexps . '(".*nrepl" "^cider.nrepl"))

  (eval . (define-clojure-indent
            (div :defn)
	    (p :defn)
            (label :defn)
            (input :defn)
            (select :defn)
	    (button :defn)
            (table :defn)
	    (com.fulcrologic.fulcro.components/transact! 1)
	    (comp/transact! 1)
	    (com.fulcrologic.fulcro.algorithms.normalized-state/swap!-> 1)
	    (defmutation '(2 :form :form (:defn)))
            (>defn  '(:defn (1)))))

  (fill-column . 80)

  (cljr-magic-require-namespaces . (("io"    . "clojure.java.io")
                                    ("set"   . "clojure.set")
                                    ("str"   . "clojure.string")
                                    ("walk"  . "clojure.walk")
                                    ("edn"   . "clojure.edn")
                                    ("zip"   . "clojure.zip")
                                    ("async" . "clojure.core.async")
                                    ("s"     . "clojure.spec.alpha")
                                    ("instant" . "clojure.instant")

                                    ("log" . "clojure.tools.logging")
                                    ("ig" . "integrant.core")
                                    ("jdbc" . "next.jdbc")

                                    ("tempid" . "com.fulcrologic.fulcro.algorithms.tempid")
                                    ("fdn" . "com.fulcrologic.fulcro.algorithms.denormalize")
                                    ("comp"  . "com.fulcrologic.fulcro.components")
                                    ("dom"   . "com.fulcrologic.fulcro.dom")
                                    ("fs"    . "com.fulcrologic.fulcro.algorithms.form-state")
                                    ("df"    . "com.fulcrologic.fulcro.data-fetch")
                                    ("dr"    . "com.fulcrologic.fulcro.routing.dynamic-routing")
                                    ("m" . "com.fulcrologic.fulcro.mutations")
                                    ("app" . "com.fulcrologic.fulcro.application"))))

 (clojurescript-mode
  (cider-default-cljs-repl . shadow)
  (cider-shadow-default-options . "main")
  (cider-offer-to-open-cljs-app-in-browser . nil))
 (cider-repl-mode
  (cider-repl-history-file . "/Users/amarcar/dev/moocar.me/ra-game/.cider-repl-history"))
 )
