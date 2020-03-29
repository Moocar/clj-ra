(ns ra.pathom
  (:require [clojure.tools.logging :as log]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [edn-query-language.core :as eql]
            [ghostwheel.core :as g :refer [=> >defn]]
            [integrant.core :as ig]
            [io.aviso.exception :as aviso]
            [ra.model.game :as m-game]
            [ra.model.player :as m-player]
            [ra.model.user :as m-user]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dev tools

(>defn entity-parse-raw
  [{:keys [parser]} query]
  [any? ::eql/query => any?]
  (dissoc (parser {} query) :com.wsscode.pathom/trace))

(>defn entity-parse
  [parser query]
  [any? ::eql/query => any?]
  (let [ast    (eql/query->ast query)
        key    (get-in ast [:children 0 :key])
        result (parser {} query)]
    (if (::p/errors result)
      #_(throw (second (first (::p/errors result))))
      (throw (ex-info "Error" (::p/errors result)))
      (if (instance? Exception result)
        (throw result)
        (get result key)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parser

(alter-var-root #'aviso/*default-frame-rules*
                (constantly [[:package "clojure.lang" :omit]
                             [:package #"sun\.reflect.*" :hide]
                             [:package "java.lang.reflect" :omit]
                             [:name #"next\.jdbc\.protocols/.*" :omit]
                             [:name #"speclj\..*" :terminate]
                             [:name #"clj-http\.client/.*" :omit]
                             [:name #"clj-http\.cookies/.*" :omit]
                             [:name #"slingshot\.support/.*" :omit]
                             [:name #"com\.wsscode\.pathom\.connect/.*" :omit]
                             [:name #"clojure\.core\.async\.impl\.ioc-macros/.*" :omit]
                             [:name #"clojure\.main/repl/read-eval-print.*" :terminate]]))

(defn preprocess-parser-plugin
  "Helper to create a plugin that can view/modify the env/tx of a top-level request.

  f - (fn [{:keys [env tx]}] {:env new-env :tx new-tx})

  If the function returns no env or tx, then the parser will not be called (aborts the parse)"
  [f]
  {::p/wrap-parser
   (fn transform-parser-out-plugin-external [parser]
     (fn transform-parser-out-plugin-internal [env tx]
       (let [{:keys [env tx] :as req} (f {:env env :tx tx})]
         (if (and (map? env) (seq tx))
           (parser env tx)
           {}))))})

(pc/defresolver index-explorer [env _]
  {::pc/input  #{:com.wsscode.pathom.viz.index-explorer/id}
   ::pc/output [:com.wsscode.pathom.viz.index-explorer/index]}
  (try
    {:com.wsscode.pathom.viz.index-explorer/index
     (get env ::pc/indexes)}
    (catch Exception e (println e))))

(defn log-requests [{:keys [env tx] :as req}]
  (log/debug "Pathom transaction:" (pr-str tx))
  req)

(defn make-resolvers []
  [m-game/resolvers
   m-player/resolvers
   m-user/resolvers
   index-explorer])

(defn process-error [env err]
  (log/error (aviso/format-exception err))
  err)

(defn make-serial-parser [{:keys [resolvers extra-env]}]
  (let [real-parser (p/parser
                     {::p/env {::p/reader                 [p/map-reader
                                                           pc/reader2
                                                           pc/open-ident-reader
                                                           p/env-placeholder-reader
                                                           ]
                               ::p/placeholder-prefixes   #{">"}
                               ;; ::p/process-error          process-error
                               ::pc/mutation-join-globals [:tempids]
                               }
                      ::p/mutate  pc/mutate
                      ::p/plugins [(pc/connect-plugin {::pc/register resolvers})
                                   (p/env-wrap-plugin #(merge % extra-env))
                                   (preprocess-parser-plugin log-requests)
                                   ;; p/error-handler-plugin
                                   (p/post-process-parser-plugin p/elide-not-found)
                                   ;; p/elide-special-outputs-plugin
                                   p/trace-plugin
                                   ]})]
    (fn wrapped-parser [env tx]
      (try
        (real-parser env (conj tx :com.wsscode.pathom/trace))
        (catch Exception e
          (println e)
          e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integrant

(defmethod ig/init-key ::parser [_ extra-env]
  (make-serial-parser {:resolvers (make-resolvers)
                       :extra-env extra-env}))
