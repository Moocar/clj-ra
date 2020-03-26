(ns ra.app.client
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.application :as app]
            [ra.app.app :as client-app]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]))

(defsc Root [this {::app/keys [active-remotes]}]
  {:query [[::app/active-remotes '_]]
   :initial-state {}}
  (dom/div {}
    (when (seq active-remotes)
      (dom/div :.ui.active.inline.loader))
    (dom/p "hi there")))

(defn ^:export refresh []
  (app/mount! client-app/APP Root "app"))

(defn ^:export start []
  (app/mount! client-app/APP Root "app")
  (dr/initialize! client-app/APP))
