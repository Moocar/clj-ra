(ns ra.app.routing
  (:require [clojure.string :as str]
            [pushy.core :as pushy]
            [ra.app.app :as client-app]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]))

(defn parse-query-params-str [s]
  (->> (str/split s "&")
       (remove str/blank?)
       (reduce (fn [m pair-str]
                 (let [[k v] (str/split pair-str "=" )]
                   (assoc m (keyword k) v)))
               {})))

(defn parse-uri [s]
  (let [route-segments (vec (remove str/blank? (rest (str/split s "/"))))
        first-n (butlast route-segments)
        last-segment (last route-segments)
        [last-segment query-params] (remove str/blank? (str/split last-segment "?"))]
    {:segments (remove nil? (concat first-n [last-segment]))
     :query-params (parse-query-params-str query-params)}))

(defn do-pushy [p]
  (let [{:keys [segments query-params]} (parse-uri p)]
    (dr/change-route! client-app/APP
                     (if (= segments [])
                       ["lobby"]
                       segments)
                     ;; HACK: /orders?a=b -> /orders?foo=bar wouldn't trigger,
                     ;; so we force every time
                     (assoc query-params ::dr/force? true))))

(defonce history
  (pushy/pushy do-pushy identity))

(defn start! []
  (pushy/start! history))

(defn route-to! [route-string]
  (pushy/set-token! history route-string))

(defn replace-to! [route-string]
  (pushy/replace-token! history route-string))
