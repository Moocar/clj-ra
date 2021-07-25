(ns ra.app.player
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [ra.app.ui :as ui]
            [ra.model.player :as m-player]
            [ra.specs.player :as player]
            [goog.object :as gobj]))

(defsc Player [_ {:keys [::player/name]}]
  {:query [::player/name
           ::player/id]
   :ident ::player/id}
  (dom/strong name))

(def ui-player (comp/factory Player {:keyfn ::player/id}))

(defsc NewForm [this {:keys [::player/id :ui/temp-name] :as props}]
  {:query         [::player/id :ui/temp-name ::player/name]
   :ident         ::player/id
   :initial-state {:ui/temp-name ""}
   :initLocalState (fn [this _]
                     {:save-ref (fn [r] (gobj/set this "name" r))})
   :componentDidMount (fn [this _ _]
                        (when-let [name-field (gobj/get this "name")]
                          (.focus name-field)))
   :route-segment ["player" ::player/id]
   :will-enter (fn [_ {:keys [::player/id]}]
                 (dr/route-immediate [::player/id (uuid id)]))}
  (dom/div :.h-screen.w-screen.flex.justify-center.items-center {}
    (dom/div :.flex.flex-col.justify-center.shadow-md.rounded.bg-gray-50.px-8.pt-6.pb-8.mb-4.items-center {}
      (dom/label :.block.text-gray-700.text-sm.font-bold.mb-2 {:htmlFor "username"}
        "What shall we call you?")
      (dom/div {}
        (ui/input props :ui/temp-name
          {:id          "username"
           :type        "text"
           :placeholder "Name"
           :ref         (comp/get-state this :save-ref)
           :onKeyUp     (fn [evt]
                          (when (= (.-keyCode evt) 13)
                            (comp/transact! this [(m-player/save
                                                   {::player/id   id
                                                    ::player/name temp-name})]
                                            {:refresh [:ui/current-player]})))
           :onChange    (fn [evt _]
                          (m/set-string! this :ui/temp-name :event evt))}))
      (ui/button {:onClick (fn []
                             (comp/transact! this [(m-player/save
                                                    {::player/id   id
                                                     ::player/name temp-name})]
                                             {:refresh [:ui/current-player]}))}
        "Submit"))))

(def ui-new-form (comp/factory NewForm {:keyfn ::player/id}))
