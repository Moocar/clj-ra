(ns ra.app.sun-disk
  (:require [com.fulcrologic.fulcro.dom :as dom]))

(defn ui [{:keys [value used? onClick too-low? large round]}]
  (dom/div :.rounded.md:rounded-full.md:h-12.md:w-12.my-2.flex.items-center.justify-center.bg-red-300.cursor-default.shadow-lg.border-2.border-gray-400.px-2.md:px-0
    (cond-> {}
      onClick (-> (assoc :onClick (fn [_] (onClick)))
                  (assoc :classes ["cursor-pointer" "hover:bg-red-500"]))
      (or too-low? used?) (update :classes concat ["opacity-50"])
      large (update :classes concat ["border-4" "border-red-700"])
      round (update :classes concat ["rounded-full" "h-12" "w-12"]))
    (if used?
      (dom/div :.flex.w-full.h-full.items-center.justify-center.opacity-0.hover:opacity-100 {} (str value))
      (str value))))

(defn ui-pass [{:keys [onClick]}]
  (dom/div :.rounded-full.h-12.w-12.my-2.flex.items-center.justify-center.bg-red-300.cursor-pointer.hover:bg-red-500.border-gray-400.border-2.shadow-lg
    {:onClick (fn [_] (onClick))}
    "Pass"))

(defn ui-large [{:keys [value]}]
  (dom/div :.rounded-full.h-20.w-20.my-2.flex.items-center.justify-center.bg-red-300.cursor-default
    {}
    (str value)))
