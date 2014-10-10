(ns wifi.haptic
  (:require [serial-port :as serial]
            [quil.core :as q]
            [clojure.core.async :as async :refer [go timeout <!]]))

(def arduino (serial/open "/dev/tty.usbmodem1421"))
(def last-motor-status (atom [0 0]))

(def fps 60)

;;(serial/on-byte arduino #(println "byte: " (char %)))
(defn set-motors [m1 m2]
  (reset! last-motor-status [m1 m2])
  (serial/write arduino (byte-array [255 m1 m2])))
(defn close []
  (serial/close arduino))
;;(close)

;; todo: function 'vibrate', function 'reset', function 'go down slowly'
(defmacro dotime [expr]
  `(let [start# (System/nanoTime)]
     ~expr
     (/ (double (- (System/nanoTime) start#)) 1e6)))
(defn vibrate
  ([] (vibrate 100))
  ([duration] (vibrate duration 1))
  ([duration intensity] (vibrate duration intensity 60))
  ([duration intensity fps]
   (go
     (let [ms (/ 1000 fps)
           times (Math/round (float (/ duration ms 2)))
           [v10 v20] @last-motor-status
           v11 v10
           v12 (+ v11 intensity)
           v21 v20
           v22 (+ v21 intensity)]
       (doseq [i (range times)]
         (<! (timeout (- ms (dotime (set-motors v11 v21)))))
         (<! (timeout (- ms (dotime (set-motors v12 v22))))))
       (set-motors v10 v20)))))

(defn reset []
  ;; todo
  )
(defn sink-slowly []
  ;; todo
  )
(defn raise-slowly []
  ;; todo
  )

(comment
  (defn setup []
    (q/frame-rate 30))
  (defn draw []
    (q/background 255)
    (q/fill 0)
    (let [m1 (q/round (q/map-range (q/mouse-x) 0 (q/width) 0 180))
          m2 (q/round (q/map-range (q/mouse-y) 0 (q/height) 0 180))]
      (q/text (str m1 ":" m2) 50 50)
      (set-motors m1 m2)))
  (q/defsketch testsketch
               :title "motor test"
               :setup setup
               :draw draw
               :size [600 400]))
