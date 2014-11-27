(ns wifi.snifvis
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [wifi.sniffer :as sn]
            [wifi.haptic2 :as haptic]
            [clojure.core.async :refer [<! <!! timeout chan pipe go go-loop] :as async]))

(def running (atom false))

(def vib-threshold 10)

(def interval 1000)
(def delay-factor (atom 300))
(def vib-duration 100)
(defn vibrate-signal-set-loop [ival-count state]
  (<!! (timeout interval))
  (swap! state assoc :delay (if (> @ival-count 0)
                              (int (* @delay-factor
                                      (/ interval (double @ival-count))))
                              interval))
  (swap! state assoc :based-on @ival-count)
  (reset! ival-count 0))
(defn vibrate-signal-update-loop [ival-count state result]
  (if (not= (@state :based-on) 0)
    (do
      (reset! result true)
      (<!! (timeout interval))
      (reset! result false)
      (<!! (timeout (max 0 (- (@state :delay) vib-duration)))))
    (<!! (timeout (@state :delay)))))
(defn vibrate-signal [ival-count]
  (let [state (atom {:delay    interval
                     :based-on 0})
        result (atom false)]
    (go (while @running (vibrate-signal-set-loop ival-count state)))
    (go (while @running (vibrate-signal-update-loop ival-count state result)))
    result))

(defn setup []
  (let [s (sn/sniff)]
    (reset! running true)
    {:sniffer      s
     :counter      (-> s sn/all sn/counter)
     :data-counter (-> s sn/data sn/counter)
     :addresses    (sn/top-addresses s)
     :size (sn/last-value (sn/accumulate + 0 (sn/data s (chan 2 (map #(if-let [len (:data.len %)] len 0))))))
     :vibrate (-> s sn/data sn/counter vibrate-signal)}))

(defn update [{:keys [] :as state}]
  state)

(defn vibrate? [vibrate lower]
  (and (not @lower) @vibrate (> (@haptic/values :servo1) vib-threshold)))

(defn draw [{:keys [counter data-counter addresses size vibrate lower] :as state}]
  (haptic/set-vibrating! (vibrate? vibrate lower))
  (reset! lower (q/key-pressed?))
  (if (q/key-pressed?)
    (haptic/adjust-motors 30 30)
    (haptic/adjust-motors 0 0))
  (q/background 255)
  (q/fill 0)
  (q/text (str "Total packets: " @counter) 12 24)
  (q/text (str "Data packets: " @data-counter) 12 (+ 24 18))
  (q/text (str "Total addresses: " (count @addresses)) 12 (+ 24 18 18))
  (q/text (str "Top addresses:\n" (sn/addresses>str @addresses)) 12 (+ 24 18 18 18))
  (q/text (str "Size: " @size) 12 (+ 24 (* 8 18)))
  (q/text (str "Vibrate? " @vibrate) 12 (+ 24 (* 9 18)))
  state)

; most packets: 78:92:9c:0d:7d:d0
; later: 28:94:0f:aa:97:5f
; later: 7c:d1:c3:ec:c7:6f
; later: 00:24:d7:26:81:94 (ch 1)

(defn on-close [{:keys [sniffer] :as state}]
  (reset! running false)
  (sn/stop sniffer))

(q/defsketch snifvis
             :setup setup
             :update update
             :draw draw
             :on-close on-close
             :size [300 200]
             :middleware [m/fun-mode])