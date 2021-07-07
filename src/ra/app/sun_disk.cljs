(ns ra.app.sun-disk
  (:require [com.fulcrologic.fulcro.dom :as dom]))

(defn ui [{:keys [value used? onClick too-low? large]}]
  (dom/div :.rounded-full.h-12.w-12.my-2.flex.items-center.justify-center.bg-red-300.cursor-default.shadow-md
    (cond-> {}
      onClick (-> (assoc :onClick (fn [_] (onClick)))
                  (assoc :classes ["cursor-pointer" "hover:bg-red-500"]))
      too-low? (update :classes concat ["cursor-not-allowed" "opacity-50"])
      large (update :classes concat ["border-4" "border-red-700"]))
    (if used?
      (dom/div :.flex.w-full.h-full.items-center.justify-center.opacity-0.hover:opacity-100 {} (str value))
      (str value))))

(defn ui-pass [{:keys [onClick]}]
  (dom/div :.rounded-md.h-8.w-16.my-2.flex.items-center.justify-center.bg-red-300.cursor-pointer.hover:bg-red-500
    {:onClick (fn [_] (onClick))}
    "Pass"))

(defn ui-large [{:keys [value]}]
  (dom/div :.rounded-full.h-20.w-20.my-2.flex.items-center.justify-center.bg-red-300.cursor-default
    {}
    (str value)))
