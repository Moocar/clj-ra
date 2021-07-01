(ns ra.app.sun-disk
  (:require [com.fulcrologic.fulcro.dom :as dom]))

(defn ui [{:keys [value used? onClick too-low?]}]
  (dom/div :.rounded-full.h-8.w-8.my-2.flex.items-center.justify-center.bg-red-300.cursor-default
    (cond-> {}
      onClick (-> (assoc :onClick (fn [_] (onClick)))
                  (assoc :classes ["cursor-pointer"]))
      too-low? (update :classes concat ["cursor-not-allowed" "opacity-50"]))
    (if used?
      (dom/div :.flex.w-full.h-full.items-center.justify-center.opacity-0.hover:opacity-100 {} (str value))
      (str value))))
