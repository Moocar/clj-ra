(ns ra.pathom
  (:require [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.core :as p]
            [edn-query-language.core :as eql]
            [integrant.core :as ig]
            [ra.model.bot :as m-bot]
            [ra.model.game :as m-game]
            [ra.model.player :as m-player]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dev tools

(defn entity-parse-raw
  [{:keys [parser]} query]
  (dissoc (parser {} query) :com.wsscode.pathom/trace))

(defn entity-parse
  [parser query]
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

(pc/defresolver index-explorer [env _]
  {::pc/input  #{:com.wsscode.pathom.viz.index-explorer/id}
   ::pc/output [:com.wsscode.pathom.viz.index-explorer/index]}
  (try
    {:com.wsscode.pathom.viz.index-explorer/index
     (get env ::pc/indexes)}
    (catch Exception e e)))

(defn log-requests [{:keys [env tx] :as req}]
  (println "Pathom transaction:" (pr-str tx))
  req)

(defn make-resolvers []
  [m-game/resolvers
   m-player/resolvers
   m-bot/resolvers
   index-explorer])

(defn process-error [env err]
  (if (:user-error? (ex-data err))
    (println (ex-message err) (dissoc (ex-data err) :user-error?))
    (println err))
  {:error {:msg (.getMessage err)
           :data (ex-data err)}})

(defn make-serial-parser [{:keys [resolvers extra-env]}]
  (let [real-parser (p/parser
                     {::p/env {::p/reader                 [p/map-reader
                                                           pc/reader2
                                                           pc/open-ident-reader
                                                           p/env-placeholder-reader
                                                           ]
                               ::p/placeholder-prefixes   #{">"}
                               ::p/process-error          process-error
                               ::pc/mutation-join-globals [:tempids]
                               }
                      ::p/mutate  pc/mutate
                      ::p/plugins [(pc/connect-plugin {::pc/register resolvers})
                                   (p/env-wrap-plugin #(merge % extra-env))
                                   p/error-handler-plugin
                                   (p/post-process-parser-plugin p/elide-not-found)
                                   ;; p/elide-special-outputs-plugin
                                   p/trace-plugin
                                   ]})]
    (fn wrapped-parser [env tx]
      (try
        (real-parser env tx #_(conj tx :com.wsscode.pathom/trace))
        (catch Exception e
          e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integrant

(defmethod ig/init-key ::parser [_ extra-env]
  (make-serial-parser {:resolvers (make-resolvers)
                       :extra-env extra-env}))
