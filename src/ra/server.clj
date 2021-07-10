(ns ra.server
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [cognitect.transit :as transit]
            [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
            [com.fulcrologic.fulcro.networking.websockets :as fws]
            [com.fulcrologic.fulcro.server.api-middleware :as fmw :refer [wrap-api]]
            [hiccup.core :refer [html]]
            [integrant.core :as ig]
            [org.httpkit.server :as http]
            [ra.date :as date]
            [ra.specs :as rs]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]])
  (:import [com.cognitect.transit TransitFactory WriteHandler]
           com.fulcrologic.fulcro.algorithms.tempid.TempId
           [java.io ByteArrayOutputStream OutputStream]
           java.time.ZonedDateTime
           java.util.function.Function))

(s/def ::port pos-int?)
(s/def ::handler fn?)
(s/def ::resource-name rs/non-empty-string?)
(s/def ::manifest-edn vector?)
(s/def ::asset-path rs/non-empty-string?)
(s/def ::main-js-path rs/non-empty-string?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pathom viz workaround
;; https://wilkerlucio.github.io/pathom/v2/pathom/2.2.0/connect/exploration.html#_fixing_transit_encoding_issues

(deftype DefaultHandler []
  WriteHandler
  (tag [this v] "unknown")
  (rep [this v] (pr-str v)))

(deftype TempIdHandler []
  WriteHandler
  (tag [_ _] tempid/tag)
  (rep [_ r] (.-id ^TempId r))
  (stringRep [_ r] (str tempid/tag "#" r))
  (getVerboseHandler [_] nil))

(deftype ZonedDateTimeHandler []
  WriteHandler
  (tag [_ _] "zdt")
  (rep [_ d] [(date/zdt->date d) (str (.getOffset d))]))


(defn writer
  "Creates a writer over the provided destination `out` using
   the specified format, one of: :msgpack, :json or :json-verbose.
   An optional opts map may be passed. Supported options are:
   :handlers - a map of types to WriteHandler instances, they are merged
   with the default-handlers and then with the default handlers
   provided by transit-java.
   :transform - a function of one argument that will transform values before
   they are written."
  ([out type] (writer out type {}))
  ([^OutputStream out type {:keys [handlers transform default-handler]}]
   (if (#{:json :json-verbose :msgpack} type)
     (let [handler-map (merge transit/default-write-handlers handlers)]
       (transit/->Writer
         (TransitFactory/writer (#'transit/transit-format type) out handler-map default-handler
           (when transform
             (reify Function
               (apply [_ x]
                 (transform x)))))))
     (throw (ex-info "Type must be :json, :json-verbose or :msgpack" {:type type})))))

(defn write-transit [x]
  (let [baos (ByteArrayOutputStream.)
        w    (writer baos :json {:handlers {TempId (TempIdHandler.)
                                            ZonedDateTime (ZonedDateTimeHandler.)} ;; transit-write-handlers ; use your handlers here
                                 :default-handler (DefaultHandler.)})
        _    (transit/write w x)
        ret  (.toString baos)]
    (.reset baos)
    ret))

(defn- set-content-type
  "Returns an updated Ring response with the a Content-Type header corresponding
  to the given content-type. This is defined here so non-ring users do not need ring."
  [resp content-type]
  (assoc-in resp [:headers "Content-Type"] (str content-type)))

(defn wrap-transit-response
  "Middleware that converts responses with a map or a vector for a body into a
  Transit response.
  Accepts the following options:
  :encoding - one of #{:json :json-verbose :msgpack}
  :opts     - a map of options to be passed to the transit writer"
  {:arglists '([handler] [handler options])}
  [handler & [{:as options}]]
  (let [{:keys [encoding] :or {encoding :json}} options]
    (assert (#{:json :json-verbose :msgpack} encoding) "The encoding must be one of #{:json :json-verbose :msgpack}.")
    (fn [request]
      (let [response (handler request)]
        (if (coll? (:body response))
          (let [transit-response (update-in response [:body] write-transit)]
            (if (contains? (:headers response) "Content-Type")
              transit-response
              (set-content-type transit-response (format "application/transit+%s; charset=utf-8" (name encoding)))))
          response)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Websockets

#_(defrecord MyListener []
  fws-protocols/WSListener
  (client-added [this ws-net cid]
    (println "client added" cid)
    (fws-protocols/push ws-net cid :x {:value 1}))
  (client-dropped [this ws-net cid]
    (println "cliend dropped cid")))

(defmethod ig/init-key ::websockets [_ {:keys [pathom-parser]}]
  (let [parser (atom (delay pathom-parser))]
    {:parser     parser
     :websockets (fws/start! (fws/make-websockets
                              (fn [env query]
                                (@@parser env query))
                              {:http-server-adapter (get-sch-adapter)
                               :parser-accepts-env? true
                               :sente-options       {:csrf-token-fn nil}}))}))

(defmethod ig/halt-key! ::websockets [_ {:keys [websockets]}]
  (fws/stop! websockets))

(defmethod ig/suspend-key! ::websockets [_ {:keys [parser]}]
  (reset! parser (promise)))

(defmethod ig/resume-key ::websockets [_ opts _ old-impl]
  (deliver @(:parser old-impl) (:pathom-parser opts))
  old-impl)

(defmethod ig/resolve-key ::websockets [_ {:keys [websockets]}]
  websockets)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server

(defn index-html [{:keys [main-js-path]}]
  (html
   [:html
    {:lang  "en"
     :class "text-gray-900 leading-light"}
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name    "viewport"
             :content "width=device-width, initial-scale=1.0"}]
     [:title "Ra?"]
     [:link {:rel  "stylesheet"
             :type "text/css"
             :href "/styles.css"}]
     [:link {:rel  "shortcut icon"
             :type "image/svg"
             :href "/favicon.svg"}]]
    [:body {:class "bg-gray-50"}
     [:div {:id "app"} ""]
     [:script {:src main-js-path}]]]))

(defn spa-handler [config]
  (fn [_]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (index-html config)}))

(defn transit-reader-handlers []
  {"zdt" (transit/read-handler date/tagged-value->zdt)})

(defn make-middleware [{:keys [pathom-parser websockets] :as config}]
  (-> (spa-handler config)
      ;; not-found-handler
      (wrap-api {:uri    "/api"
                 :parser (fn [query]
                           (try
                             (pathom-parser {} query)
                             (catch Exception e
                               (println "caught exception")
                               (println e)
                               e)))})
      (fmw/wrap-transit-params {:opts {:handlers (transit-reader-handlers)}})
      (wrap-transit-response)
      (fws/wrap-api websockets)
      wrap-keyword-params
      wrap-params
      (wrap-resource "public")
      wrap-content-type
      wrap-not-modified))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integrant

(defmethod ig/init-key ::manifest-edn [_ {:keys [resource-name]}]
  (edn/read-string (slurp (io/resource resource-name))))

(defmethod ig/pre-init-spec ::manifest-edn [_]
  (s/keys :req-un [::resource-name]))

(defmethod ig/init-key ::main-js-path [_ {:keys [manifest-edn asset-path]}]
  (let [output-name (:output-name (first manifest-edn))]
    (str asset-path "/" output-name)))

(defmethod ig/pre-init-spec ::main-js-path [_]
  (s/keys :req-un [::manifest-edn ::asset-path]))

(defmethod ig/init-key ::handler [_ config]
  (make-middleware config))

(defmethod ig/init-key ::server [_ {:keys [handler port]}]
  (println "initializing server")
  (let [handler (atom (delay handler))
        result {:handler handler
                :server  (http/run-server #(@@handler %) {:port port})}]
    (println "server started")
    result))

(defmethod ig/halt-key! ::server [_ {:keys [server]}]
  (server))

(defmethod ig/suspend-key! ::server [_ {:keys [handler]}]
  (reset! handler (promise)))

(defmethod ig/resume-key ::server [key opts old-opts old-impl]
  (if (= (:port opts) (:port old-opts))
    (do (deliver @(:handler old-impl) (:handler opts))
        old-impl)
    (do (ig/halt-key! key old-impl)
        (ig/init-key key opts))))

(defmethod ig/resolve-key ::server [_ {:keys [server]}]
  server)

(defmethod ig/pre-init-spec ::server [_] (s/keys :req-un [::port ::handler]))
(defmethod ig/pre-init-spec ::handler [_] nil)
