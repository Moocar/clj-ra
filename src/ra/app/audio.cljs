(ns ra.app.audio
  (:require [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [clojure.core.async :as async]))

(defmutation store-ding [{:keys [buffer]}]
  (action [env]
    (swap! (:state env) assoc :ui/ding-buffer buffer)))

(defmutation store-ra [{:keys [buffer]}]
  (action [env]
    (swap! (:state env) update :ui/ra-buffers conj buffer)))

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

(defn load-and-store-ras! [app]
  (doseq [file ["/Ra1.mp3" "/Ra2.mp3" "/Ra3.mp3" "/Ra4.mp3" "/Mum-Ra1.mp3" "/Mum-Ra2.mp3" "/Dad-Ra1.mp3"]]
    (.then (.fetch js/window file)
           (fn [response]
             (.then (.arrayBuffer response)
                    (fn [array-buffer]
                      (.then (.decodeAudioData audio-ctx array-buffer)
                             (fn [audio-buffer]
                               (comp/transact! app [(store-ra {:buffer audio-buffer})])))))))))

(defn play-buffer! [buffer]
  (let [source    (.createBufferSource audio-ctx)
        gain-node (.createGain audio-ctx)]
    (set! (.. source -buffer) buffer)
    (set! (.. gain-node -gain -value) 0.5) ;; 50% volume
    (.connect gain-node (.-destination audio-ctx))
    (.connect source gain-node)
    (.start source)))

(defn play-ding! [state-map]
  (when-let [buffer (get state-map :ui/ding-buffer)]
    (play-buffer! buffer)))

(defn -play-random-ra! [state-map]
  (when-let [ra-buffers (get state-map :ui/ra-buffers)]
    (let [buffer (rand-nth ra-buffers)]
      (play-buffer! buffer))))

(defonce ra-chan (async/chan))

(defn start-ra-listen-loop! []
  (let [last-ra (atom (js/Date.now))]
    (async/go-loop []
      (when-let [state-map (async/<! ra-chan)]
        (when (< (inc @last-ra) (js/Date.now))
          (reset! last-ra (js/Date.now))
          (-play-random-ra! state-map))
        (recur)))))

(defn play-random-ra! [state-map]
  (async/put! ra-chan state-map))
