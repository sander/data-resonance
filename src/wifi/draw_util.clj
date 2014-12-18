(ns wifi.draw-util
  (:require [quil.core :as q]
            [wifi.util :refer [millis]]))

(defn divide [params size]
  (let [fixed (transduce (filter number?) + params)
        nf (transduce (map #(if (= % :flex) 1 0)) + params)
        fs (/ (- size fixed) nf)]
    (map #(if (number? %) % fs) params)))
(defn grid [w h cols rows] [(divide cols w) (divide rows h)])
(defn in-grid [[cols rows] col row]
  [(apply + (take col cols))
   (apply + (take row rows))
   (nth cols col)
   (nth rows row)])

(defn create-chart [& {:keys [position size show-ms range stroke grid start-time]}]
  (let [[px py w h] (if grid (apply in-grid (first grid) (rest grid)))
        grid-pos (if grid [px py])
        grid-size (if grid [w h])]
    {:position   (or position grid-pos [0 0])
     :size       (or size grid-size [(q/width) (q/height)])
     :show-ms    (or show-ms 20000)
     :range      (or range [0 3000])
     :last-time  (or start-time (millis))
     :start-time (or start-time (millis))
     :background 0
     :stroke     (or stroke 255)
     :iteration 0}))
(defn update-chart
  [{:keys [current last-time position size show-ms range last-value start-time iteration] :as chart} f]
  (let [t (millis)
        ms-per-point (/ show-ms (size 0))
        value (f t last-time last-value)
        extended-x (/ (- t start-time) ms-per-point)
        x1 (if (nil? current)
             (position 0)
             (mod extended-x (size 0)))
        #_(if (nil? current)
             (position 0)
             (+ (current 0) (/ (- t last-time) ms-per-point)))
        y1 (q/map-range
             (or value 0)
             (range 0) (range 1)
             (+ (position 1) (size 1)) (position 1))
        new-iteration (int (/ extended-x (size 0)))
        clear (and current (> new-iteration iteration))]
    (assoc chart
           :clear clear
           :previous (if clear nil current)
           :current (if clear [(position 0) y1] [x1 y1])
           :last-time (if (= last-value value) last-time t)
           :last-value value
           :iteration new-iteration)))
(defn draw-chart [{:keys [previous current clear position size background stroke]}]
  (if clear
    (do
      (q/no-stroke)
      (q/fill background)
      (apply q/rect (concat position size)))
    (when (and previous current)
      (q/stroke stroke)
      (let [cx #(q/constrain (% 0) (position 0) (+ (position 0) (size 0)))
            cy #(q/constrain (% 1) (position 1) (+ (position 1) (size 1) -1))]
        (q/line
          (cx previous) (cy previous)
          (cx current) (cy current))))))