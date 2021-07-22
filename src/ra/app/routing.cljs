(ns ra.app.routing
  (:require [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [clojure.string :as str]))

(defn handle-window! [app]
  (let [path     (-> js/window .-location .-pathname)
        elements (.split path "/")
        route (vec (rest elements))]
    (if (= [""] route)
      (dr/change-route! app ["lobby"])
      (dr/change-route! app route))))

(defn to! [app to]
  (dr/change-route! app to)
  (.pushState (.-history js/window) #js {} "" (str "/" (str/join "/" (map str to)))))

;; Ugly
(defn back! [app to]
  (dr/change-route! app to)
  (.back (.-history js/window)))
