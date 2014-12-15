(ns wifi.visualize
  (:require [clojure.core.async :as async]
            [quil.core :as q]
            [quil.middleware :as m]
            [wifi.draw-util :refer [create-chart update-chart draw-chart]]
            [wifi.sniffer :refer [sniff stop all data]]
            [wifi.wifi-util :refer [bytes-chan bytes-per-interval]]
            [wifi.util :refer [interpolate millis count-values last-item]]))

(def ival 1000)

;; TODO why are the charts out of sync - maybe need more central timing?

(defn divide [params]
  (let [fixed (transduce (filter number?) + params)]
    fixed))
(divide [20 :flex 20 :flex])
(defn grid [w h cols rows]
  )
(grid 768 432
      [:flex]
      [20 :flex 20 :flex])
;; TODO make it easier to show multiple charts

(defn setup []
  (q/background 0)
  (q/stroke 255)
  (let [sn (sniff)]
    {:sniffer sn
     :last-count-all (last-item (count-values (all sn) ival))
     :last-count-data (last-item (count-values (data sn) ival))
     :last-bytes (last-item (bytes-per-interval (data sn) ival))
     :charts {:frequency-all (create-chart :stroke 120)
              :frequency-data (create-chart :stroke 100)
              :bytes-per-second (create-chart :stroke 255
                                              :range [0 100000])}}))

(defn update [state]
  (-> state
      (update-in [:charts :frequency-all] update-chart (interpolate (:last-count-all state) ival))
      (update-in [:charts :frequency-data] update-chart (interpolate (:last-count-data state) ival))
      (update-in [:charts :bytes-per-second] update-chart (interpolate (:last-bytes state) ival))))

(defn draw [{:keys [] :as state}]
  ;(q/background 0)
  ;(q/fill 255)
  ;(q/text (pr-str state) 50 50)
  ;(q/background 0)
  (draw-chart (get-in state [:charts :frequency-all]))
  (draw-chart (get-in state [:charts :frequency-data]))
  (draw-chart (get-in state [:charts :bytes-per-second]))
  ;(q/fill 255)
  ;state
  )

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