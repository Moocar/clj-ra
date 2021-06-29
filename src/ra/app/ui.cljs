(ns ra.app.ui
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.components :as comp]))

(defn button [options text]
  (dom/button :.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded.focus:outline-none.focus:shadow-outline
              (merge {:type "button"}
                     options)
              text))

(defn input [props field options]
  (dom/input :.shadow.appearance-none.border.rounded.w-full.py-2.px-3.text-gray-700.mb-3.leading-tight.focus:outline-none.focus:shadow-outline
             (merge {:type    "text"
                     :value   (or (get props field) "")}
                    options)))