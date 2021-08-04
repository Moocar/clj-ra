(ns ra.app.audio
  (:require [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))

(defmutation store-ding [{:keys [buffer]}]
  (action [env]
    (swap! (:state env) assoc :ui/ding-buffer buffer)))

(defonce audio-ctx
  (let [AudioContext (or js/window.AudioContext js/window.webkitAudioCOntext)]
    (AudioContext.)))

(defn load-and-store-ding! [app]
  (.then (.fetch js/window "/ding.wav")
         (fn [response]
           (.then (.arrayBuffer response)
                  (fn [array-buffer]
                    (.then (.decodeAudioData audio-ctx array-buffer)
                           (fn [audio-buffer]
                             (comp/transact! app [(store-ding {:buffer audio-buffer})]))))))))

(defn play-ding! [state-map]
  (when-let [buffer (get state-map :ui/ding-buffer)]
    (let [source (.createBufferSource audio-ctx)
          gain-node (.createGain audio-ctx)]
      (set! (.. source -buffer) buffer)
      (set! (.. gain-node -gain -value) 0.5) ;; 50% volume
      (.connect gain-node (.-destination audio-ctx))
      (.connect source gain-node)
      (.start source))))
