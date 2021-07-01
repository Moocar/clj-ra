(ns ra.app.sun-disk
  (:require [com.fulcrologic.fulcro.dom :as dom]))

(defn ui [{:keys [value used? onClick]}]
  (dom/div :.rounded-full.h-8.w-8.my-2.flex.items-center.justify-center.bg-red-200
    (cond-> {}
      onClick (assoc :onClick (fn [_] (onClick)))
      used? (assoc :classes ["opacitiy-50"]))
    (str value)))
