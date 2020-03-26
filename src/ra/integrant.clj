(ns ra.integrant
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(defn init-logging []
  (org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
  (org.slf4j.bridge.SLF4JBridgeHandler/install))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(defn config []
  (aero/read-config (io/resource "ra/system.edn")))

(defn prep
  ([]
   (let [config (config)]
     (init-logging)
     (ig/load-namespaces config)
     config))
  ([ks]
   (let [config (config)]
     (ig/load-namespaces config ks)
     config)))

(defn start-system
  ([]
   (let [config (prep)]
     (ig/init config)))
  ([ks]
   (let [config (prep ks)]
     (ig/init config ks))))

(defmethod ig/init-key ::offline? [_ x] x)
