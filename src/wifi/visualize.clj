(ns wifi.visualize
  (:require [clojure.core.async :as async]
            [quil.core :as q]
            [quil.middleware :as m]
            [wifi.arduino :as arduino]
            [wifi.draw-util :refer [grid in-grid create-chart update-chart draw-chart constrain]]
            [wifi.sniffer :refer [sniff stop all data]]
            [wifi.wifi-util :refer [bytes-chan bytes-per-interval]]
            [wifi.util :refer [interpolate millis count-values last-item]]))

(def ival 500)
(def for-real true)
(def table true)
(def motor-l (if table [130 50]))
(def motor-r (if table [130 50]))

(defn draw-header [text gr col row]
  (let [[x y] (in-grid gr col row)]
    (q/text text (+ x 4) (+ y 4))))

(defn setup []
  (q/background 0)
  (q/no-stroke)
  (q/fill 255)
  (q/text-align :left :top)
  (if for-real (arduino/start))
  (let [sn (sniff)
        gr (grid (q/width) (q/height) [:flex] [24 :flex
                                               24 :flex
                                               24 128
                                               24 128])
        start-time (millis)]
    (draw-header "Packets per second" gr 0 0)
    (draw-header "Bytes per second" gr 0 2)
    (draw-header "Motor feedback" gr 0 4)
    (draw-header "Pressure" gr 0 6)
    {:sniffer sn
     :last-count-all (last-item (count-values (all sn) ival))
     :last-count-data (last-item (count-values (data sn) ival))
     :last-bytes (last-item (bytes-per-interval (data sn) ival))
     :last-arduino-data (last-item arduino/values)
     :charts {:frequency-all (create-chart :stroke 120
                                           :grid [gr 0 1]
                                           :start-time start-time)
              :frequency-data (create-chart :stroke 255
                                            :grid [gr 0 1]
                                            :start-time start-time)
              :bytes-per-second (create-chart :stroke 255
                                              :range [0 14]
                                              ;:value-scale #(Math/log10 %)
                                              :grid [gr 0 3]
                                              :start-time start-time)
              :motor-l (create-chart :stroke 127
                                     :range [0 1023]
                                     :grid [gr 0 5]
                                     :start-time start-time)
              :motor-r (create-chart :stroke 255
                                     :range [0 1023]
                                     :grid [gr 0 5]
                                     :start-time start-time)
              :pressure-l (create-chart :stroke 127
                                        :range [0 1023]
                                        :grid [gr 0 7]
                                        :start-time start-time)
              :pressure-r (create-chart :stroke 255
                                        :range [0 1023]
                                        :grid [gr 0 7]
                                        :start-time start-time)}}))

(defn update [state]
  ;(println (:last-bytes state))
  ;(println (-> @(:last-bytes state) (or 0) (max 1) (Math/log10)))
  (-> state
      (update-in [:charts :frequency-all] update-chart (interpolate (:last-count-all state) ival))
      (update-in [:charts :frequency-data] update-chart (interpolate (:last-count-data state) ival))
      (update-in [:charts :bytes-per-second] update-chart (interpolate (delay (-> @(:last-bytes state) (or 0) (max 1) (Math/log10))) ival))
      (update-in [:charts :motor-l] update-chart (interpolate (:last-arduino-data state) [:motor-l] 100))
      (update-in [:charts :motor-r] update-chart (interpolate (:last-arduino-data state) [:motor-r] 100))
      (update-in [:charts :pressure-l] update-chart (constantly (or (:pressure-l @(:last-arduino-data state)) 0)))
      (update-in [:charts :pressure-r] update-chart (constantly (or (:pressure-r @(:last-arduino-data state)) 0)))
      ;(update-in [:charts :bytes-per-second :range 1] (fn [_] @max-bytes-per-second))
      ))

(defn draw [{:keys [charts last-arduino-data] :as state}]
  (doseq [[_ c] charts] (draw-chart c))
  ;(arduino/set-motors (q/map-range ()))
  (let [
        ;min 0
        ;max 4
        min 0
        max (get-in state [:charts :bytes-per-second :range 1])
        orig-v (get-in charts [:bytes-per-second :last-value])
        w (constrain orig-v min max)
        v w
        ;w (Math/log10 (or orig-v 0))
        ;v (constrain w min max)
        left (int (apply q/map-range v min max motor-l))
        right (int (apply q/map-range v min max motor-r))]
    ;(println orig-v w left right)
    (if for-real (arduino/set-motors left right)))

  (comment
    (q/fill 0)
    (q/rect 0 0 (q/width) 40)
    (q/fill 255)
    (q/text (pr-str @last-arduino-data) 0 10)))

(defn dbg [f]
  (fn [v]
    (let [n (f v)]
      (println n)
      n)))

(defn key-pressed [state {:keys [key]}]
  (let [cur [:charts :bytes-per-second :range 1]]
    (case key
      :up (update-in state cur (dbg inc))
      :down (update-in state cur (dbg dec))
      state)))

(defn on-close [state]
  (stop (:sniffer state))
  (if for-real (arduino/stop)))

(q/defsketch visualize
             :title "Wi-Fi"
             :setup setup
             :update update
             :draw draw
             :on-close on-close
             :key-pressed key-pressed
             :size [768 768]
             :features [:keep-on-top]
             :middleware [m/fun-mode])