(ns wifi.debug
  (:require [quil.core :as q]
            [wifi.haptic :as hap]
            [wifi.interaction :as ix]
            [wifi.sniffer :as sn]
            [clojure.core.async :as async]))

(def mode (atom ::manual))

(def interval 1000)
(def vib-delay-factor 10)
(def vib-duration 60)

(def listen (hap/listen3))
(def touch (listen 0))
(def dist (listen 1))

(def last-touch (atom 0))
(def last-dist (atom 0))

(async/go (while (= @mode ::manual)
            (reset! last-touch (async/<! touch))))
(async/go (while (= @mode ::manual)
            (reset! last-dist (async/<! dist))))

(def auto (atom {:sniff nil
                 :vib-delay interval
                 :vib-delay-based-on 0}))

;(def vib-delay (atom interval))
;(def vib-delay-based-on (atom 0))

(defn start-auto-mode!
  []
  (let [sniff (sn/sniff)
        only-data (async/chan)
        ival-count (sn/counter only-data)]
    (async/sub (:pub sniff) :data only-data)
    (async/go (while (= @mode ::auto)
                (async/<! (async/timeout interval))
                (swap! auto assoc :vib-delay (if (> @ival-count 0)
                                               (int (* vib-delay-factor
                                                       (/ interval (double @ival-count))))
                                               interval))
                (swap! auto assoc :vib-delay-based-on @ival-count)
                (reset! ival-count 0)))
    (async/go (while (= @mode ::auto)
                (if (= (@auto :vib-delay-based-on) 0)
                  (async/<! (async/timeout (@auto :vib-delay)))
                  (do
                    (hap/set-vibrating! true)
                    (async/<! (async/timeout vib-duration))
                    (hap/set-vibrating! false)
                    (async/<!! (async/timeout (max 0 (- (@auto :vib-delay) vib-duration))))))))
    ;(ix/ix)
    (swap! auto assoc :sniff sniff)))

(defn end-auto-mode!
  []
  (async/unsub-all (:pub (@auto :sniff)))
  (sn/stop (@auto :sniff))
  ;(@ix/ix-instance)
  )

(defn set-mode!
  [new-mode]
  (when (not= @mode new-mode)
    (reset! mode new-mode)
    (if (= new-mode ::manual)
      (do
        (end-auto-mode!)
        (println "running in manual mode"))
      (do
        (start-auto-mode!)
        (println "running in auto mode")))))

(def bar-label-width 120)
(def bar-width 120)
(def bar-height 30)
(defn draw-bar
  [pos label value min max]
  (let [y (- (q/height) (* (inc pos) bar-height))]
    (q/fill 0)
    (q/text-align :right :center)
    (q/text label
            (- bar-label-width 10)
            (+ (* 0.5 bar-height) y))
    (q/rect bar-label-width
            y
            bar-width
            bar-height)
    (q/fill 100)
    (q/rect bar-label-width
            y
            (q/map-range value min max 0 bar-width)
            bar-height)
    (q/fill 255)
    (q/text (str value)
            (+ bar-label-width bar-width -10)
            (+ (* 0.5 bar-height) y))))
(defn setup []
  (q/frame-rate 30))
(defn draw []
  (if (q/key-pressed?)
    (case (q/key-as-keyword)
      :a (set-mode! ::auto)
      :m (set-mode! ::manual)
      nil))
  (let [invert false]
    (q/background (if invert 0 255))
    (q/fill (if invert 255 0)))
  (let [m1 (q/round (q/map-range (q/mouse-x) 0 (q/width) 0 180))
        m2 (q/round (q/map-range (q/mouse-y) 0 (q/height) 0 180))]
    (q/text-align :left :top)
    (q/text (str @mode " "
                 (:vib-delay-based-on @auto))
            50 50)
    (if (= @mode ::manual)
      (do
        (hap/set-vibrating! (q/mouse-pressed?))
        (hap/set-motors m1 m2))))
  (draw-bar 0 "touch" @last-touch 0 255)
  (draw-bar 1 "distance" @last-dist 0 127)
  (draw-bar 2 "motor 2" (@hap/last-motor-status 1) 0 180)
  (draw-bar 3 "motor 1" (@hap/last-motor-status 0) 0 180)
  (draw-bar 4 "vibrating" (if @hap/vibrating 1 0) 0 1)
  (comment
    (let [w 30
          h 120
          t (- (q/height) h)]
      (q/rect 0 t w h)
      (q/rect w t w h)
      (q/fill 200)
      (let [y (/ (:last-touch @manual) 255)
            h' (* h y)
            y' (+ t (- h h'))]
        (q/rect 0 y' w h))
      (let [y (/ (:last-dist @manual) 127)
            h' (* h y)
            y' (+ t (- h h'))]
        (q/rect w y' w h)))))
(q/defsketch testsketch
             :title "motor test"
             :setup setup
             :draw draw
             :size [600 400])