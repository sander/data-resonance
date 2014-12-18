(ns wifi.visualize
  (:require [clojure.core.async :as async]
            [quil.core :as q]
            [quil.middleware :as m]
            [wifi.draw-util :refer [grid in-grid create-chart update-chart draw-chart]]
            [wifi.sniffer :refer [sniff stop all data]]
            [wifi.wifi-util :refer [bytes-chan bytes-per-interval]]
            [wifi.util :refer [interpolate millis count-values last-item]]))

(def ival 1000)

;; TODO why are the charts out of sync - maybe need more central timing?

;; TODO easily adjust data thresholds

(defn setup []
  (q/background 0)
  (q/no-stroke)
  (q/fill 255)
  (q/text-align :left :top)
  (let [sn (sniff)
        gr (grid (q/width) (q/height) [:flex] [24 :flex 24 :flex])]
    (let [[x y] (in-grid gr 0 0)]
      (q/text "Packets per second" (+ x 4) (+ y 4)))
    (let [[x y] (in-grid gr 0 2)]
      (q/text "Bytes per second" (+ x 4) (+ y 4)))
    {:sniffer sn
     :last-count-all (last-item (count-values (all sn) ival))
     :last-count-data (last-item (count-values (data sn) ival))
     :last-bytes (last-item (bytes-per-interval (data sn) ival))
     :charts {:frequency-all (create-chart :stroke 120 :grid [gr 0 1])
              :frequency-data (create-chart :stroke 255 :grid [gr 0 1])
              :bytes-per-second (create-chart :stroke 255
                                              :range [0 100000]
                                              :grid [gr 0 3])}}))

(defn update [state]
  (-> state
      (update-in [:charts :frequency-all] update-chart (interpolate (:last-count-all state) ival))
      (update-in [:charts :frequency-data] update-chart (interpolate (:last-count-data state) ival))
      (update-in [:charts :bytes-per-second] update-chart (interpolate (:last-bytes state) ival))))

(defn draw [{:keys [] :as state}]
  (draw-chart (get-in state [:charts :frequency-all]))
  (draw-chart (get-in state [:charts :frequency-data]))
  (draw-chart (get-in state [:charts :bytes-per-second])))

(defn on-close [state]
  (stop (:sniffer state)))

(q/defsketch visualize
             :title "Wi-Fi"
             :setup setup
             :update update
             :draw draw
             :on-close on-close
             :size [768 432]
             :features [:keep-on-top]
             :middleware [m/fun-mode])