(ns ra.app.ui
  (:require [com.fulcrologic.fulcro.dom :as dom]))

(defn button [options text]
  (dom/button :.bg-indigo-500.text-white.font-bold.py-2.px-4.rounded.focus:outline-none.focus:shadow-outline.active:bg-indigo-700.focus:ring-2.focus:ring-green-500
    (merge {:type "button"
            :classes (if (:disabled options)
                       ["opacity-50" "cursor-default"]
                       ["md:hover:bg-indigo-700"])}
           options)
    text))

(defn input [props field options]
  (dom/input :.shadow.appearance-none.border.rounded.w-full.py-2.px-3.text-gray-700.mb-3.leading-tight.focus:outline-none.focus:shadow-outline.focus:ring-2.focus:ring-green-500
             (merge {:type    "text"
                     :value   (or (get props field) "")}
                    options)))
