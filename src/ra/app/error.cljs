(ns ra.app.error
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [ra.model.error :as m-error]))

(defsc Error [this props]
  {:query [[:ui/global-error '_]]
   :ident (fn [_] [:component/id :error])
   :initial-state {}}
  (when-let [err (:ui/global-error props)]
    (dom/div :.h-screen.w-screen.flex.justify-center.items-center.absolute.z-50.top-0 {}
      (dom/div :.relative {}
        (dom/div :.font-bold.right-2.top-1.absolute.text-md.cursor-pointer
          {:onClick (fn [] (comp/transact! this [(m-error/clear-error {})]))}
          "X")
        (dom/div :.flex.flex-col.shadow-md.rounded.bg-red-200.px-8.pt-6.pb-8.mb-4.items-center {}
          (dom/h1 :.font-bold {} "Error")
          (dom/p err))))))

(def ui-modal (comp/factory Error))
