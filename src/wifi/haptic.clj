(ns wifi.haptic
  (:require [serial-port :as serial]
            [quil.core :as q]
            [clojure.core.async :as async :refer [chan alts! go go-loop timeout <! >! put!]]))

(def arduino (serial/open "/dev/tty.usbmodem1421"))
(def last-motor-status (atom [0 0]))

(def fps 60)

;;(serial/on-byte arduino #(println "byte: " (char %)))
(defn set-motors [m1 m2]
  (reset! last-motor-status [m1 m2])
  (serial/write arduino (byte-array [255 m1 m2])))
(defn manual []
  (reset! last-motor-status [0 0])
  (serial/write arduino (byte-array [254])))
(defn close []
  (serial/close arduino))
;;(close)

(defn listen []
  (let [ch (chan)
        status (atom 0)]
    (serial/on-byte arduino (fn [st]
                              (when (not= @status st)
                                (case st
                                  1 (put! ch :release)
                                  2 (put! ch :press)))
                              (reset! status st)))
    ch))
(defn unlisten []
  (serial/remove-listener arduino))
(defn ix [touch stop]
  (go-loop []
           (let [[msg c] (alts! [touch stop])]
             (condp = [msg c]
               [:release touch] (reset)
               [:press touch] (sink-slowly)
               nil)
             (if (not= c stop) (recur)))))

(defmacro dotime [expr]
  `(let [start# (System/nanoTime)]
     ~expr
     (/ (double (- (System/nanoTime) start#)) 1e6)))
(def vibrating (atom false))
(defn vibrate
  ([] (vibrate 100))
  ([duration] (vibrate duration 1))
  ([duration intensity] (vibrate duration intensity 60))
  ([duration intensity fps]
   (go
     (when (not @vibrating)
       (let [ms (/ 1000 fps)
             times (Math/round (float (/ duration ms 2)))
             [v10 v20] @last-motor-status
             v11 v10
             v12 (+ v11 intensity)
             v21 v20
             v22 (+ v21 intensity)]
         (reset! vibrating true)
         (doseq [i (range times)]
           (<! (timeout (- ms (dotime (set-motors v11 v21)))))
           (<! (timeout (- ms (dotime (set-motors v12 v22)))))
           ;;(<! (timeout (- ms (dotime (println "setting" v11 v21)))))
           ;;(<! (timeout (- ms (dotime (println "setting2" v12 v22)))))
           )
         (set-motors v10 v20)
         (reset! vibrating false))))))

(defn reset []
  ;; todo
  (set-motors 40 30))
(defn sink-slowly []
  ;; todo
  (set-motors 50 60)
  )
(defn raise-slowly []
  ;; todo
  )

;(reset)
(sink-slowly)

(defn sketch []
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
