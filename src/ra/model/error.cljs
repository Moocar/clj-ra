(ns ra.model.error
  (:require [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.components :as comp]))

(defmutation clear-error [_]
  (action [env]
    (swap! (:state env) assoc :ui/global-error nil)))

(defmutation set-error [{:keys [msg]}]
  (action [env]
    (swap! (:state env) assoc :ui/global-error msg)))

(defn set-error! [app msg]
  #p msg
  (comp/transact! app [(set-error {:msg msg})]))
