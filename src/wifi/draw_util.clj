(ns wifi.draw-util
  (:require [quil.core :as q]
            [wifi.util :refer [millis]]))

(defn create-chart [& {:keys [position size show-ms range stroke]}]
  {:position (or position [0 0])
   :size (or size [(q/width) (q/height)])
   :show-ms (or show-ms 20000)
   :range (or range [0 3000])
   :last-time (millis)
   :background 0
   :stroke (or stroke 255)})
(defn update-chart
  [{:keys [current last-time position size show-ms range last-value] :as chart} f]
  (let [t (millis)
        ms-per-point (/ show-ms (size 0))
        value (f t last-time last-value)
        x1 (if (nil? current)
             (position 0)
             (+ (current 0) (/ (- t last-time) ms-per-point)))
        y1 (q/map-range
             (or value 0)
             (range 0) (range 1)
             (+ (position 1) (size 1)) (position 1))
        clear (and current (> (- x1 (position 0) -1) (size 0)))]
    (assoc chart
           :clear clear
           :previous (if clear nil current)
           :current (if clear [(position 0) y1] [x1 y1])
           :last-time (if (= last-value value) last-time t)
           :last-value value)))
(defn draw-chart [{:keys [previous current clear position size background stroke]}]
  (if clear
    (do
      (q/no-stroke)
      (q/fill background)
      (apply q/rect (concat position size)))
    (when (and previous current)
      (q/stroke stroke)
      (q/line (previous 0) (previous 1) (current 0) (current 1)))))