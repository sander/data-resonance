(ns wifi.hwtest
  (:require [clojure.core.async :as async]
            [quil.core :as q]
            [quil.middleware :as m]
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

;; TODO don't forget: i actually want to count bytes!!

(defn setup []
  (q/background 0)
  (q/stroke 255)

  #_ (let [sn (sniff)]
    {:sniffer sn
     :last-count-all (last-item (count-values (all sn) ival))
     :last-count-data (last-item (count-values (data sn) ival))
     ;:last-bytes-data (last-item (sum-length (data sn) ival))
     :charts {:frequency-all (create-chart :stroke 255)
              :frequency-data (create-chart :stroke 100)}}))

(defn interpolate [value]
  (fn [now last-time last-value]
    (if (and last-time last-value)
      (let [t (/ (- now last-time) ival)]
        (+ (* (- 1 t) last-value) (* t @value)))
      @value)))

(defn update [state]
  (-> state
      (update-in [:charts :frequency-all] update-chart (interpolate (:last-count-all state)))
      (update-in [:charts :frequency-data] update-chart (interpolate (:last-count-data state)))))

(defn draw [{:keys [] :as state}]
  ;(q/background 0)
  ;(q/fill 255)
  ;(q/text (pr-str state) 50 50)
  ;(q/background 0)
  (draw-chart (get-in state [:charts :frequency-all]))
  (draw-chart (get-in state [:charts :frequency-data]))
  (q/fill 255)
  ;state
  )

(defn on-close [state]
  (stop (:sniffer state)))

(q/defsketch visualize
             :title "Hardware test"
             :setup setup
             :update update
             :draw draw
             :on-close on-close
             :size [768 432]
             :features [:keep-on-top]
             :middleware [m/fun-mode])