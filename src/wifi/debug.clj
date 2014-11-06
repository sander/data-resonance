(ns wifi.debug
  (:require [quil.core :as q]
            [wifi.haptic :as hap]
            [wifi.interaction :as ix]
            [wifi.sniffer :as sn]
            [wifi.animation :as anim]
            [wifi.filter :refer [stabilize below-threshold?]]
            [clojure.core.async :as async]))

(def mode (atom ::manual))
;; ::auto ::manual

(def submode (atom ::inactive))
;; ::inactive ::active ::detailed

(def config (atom {:dist-change-timeout 1000}))
(def state (atom {:submode-changed 0
                  :last-key-pressed nil}))

(def sensor-timeout (atom 3000))

;; TODO implement:
;; on submode change, after x seconds fix current distance;
;; when not vibrating and distance has changed more than threshold,
;; consider new mode change.

(def interval 1000)                                         ; collect packets
(def vib-delay-factor-atom (atom 100))                      ; the higher, the less vibrations
(def vib-duration 100)                                      ; how long a vibration takes

(def listen (hap/listen3))
(def touch (listen 0))
(def dist (listen 1))

(def last-touch (atom 0))
(def last-dist (atom 0))

(def last-touch-stable (atom 0))
(def last-dist-stable (atom 0))

(stabilize last-touch last-touch-stable touch)
(stabilize last-dist last-dist-stable dist)

(def auto (atom {:sniff nil
                 :vib-delay interval
                 :vib-delay-based-on 0}))

(defn auto-mode? [] (= @mode ::auto))
(defn manual-mode? [] (= @mode ::manual))
(defn had-packets? [] (= (@auto :vib-delay-based-on) 0))

(defn wait! [ms] (async/<! (async/timeout ms)))

(defn start-auto-mode!
  []
  (let [sniff (sn/sniff)
        only-data (async/chan)
        ival-count (sn/counter only-data)]
    (async/sub (:pub sniff) :data only-data)
    (async/go
      (while (auto-mode?)
        (wait! interval)
        (swap! auto assoc :vib-delay (if (> @ival-count 0)
                                       (int (* @vib-delay-factor-atom
                                               (/ interval (double @ival-count))))
                                       interval))
        (swap! auto assoc :vib-delay-based-on @ival-count)
        (reset! ival-count 0)))
    (async/go
      (while (auto-mode?)
        (if (had-packets?)
          (do
            (hap/set-vibrating! true)
            (wait! vib-duration)
            (hap/set-vibrating! false)
            (wait! (max 0 (- (@auto :vib-delay) vib-duration))))
          (wait! (@auto :vib-delay)))))
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

(defn lerp
  [v w t]
  (+ (* (- 1 t) v)
     (* t w)))
(defn rlerp
  [& args]
  (Math/round (apply lerp args)))

(def motor (anim/animator))
(defn move [p1 p2]
  (let [[s1 s2] @hap/last-motor-status]
    (anim/set-state motor #(hap/set-motors
                            (anim/rlerp s1 p1 %)
                            (anim/rlerp s2 p2 %)) 500)))

(defn set-submode!
  [new]
  (reset! submode new)
  (println "setting submode" new)
  (apply move (case @submode
                ::inactive [9 5]
                ::active [42 19]
                ::detailed [55 61]))
  (swap! state assoc :submode-changed (anim/millis)))

(set-submode! ::inactive)

(defn ready-to-accept-change?
  []
  (not (below-threshold?
         (anim/millis)
         (@state :submode-changed)
         (@config :dist-change-timeout))))

(defn on-dist-change
  [key ref old new]
  (if (ready-to-accept-change?)
    (let [dir (if (> new old) :up :down)]
      (if (= dir :down)
        (case @submode
          ::inactive
          (println "would set submode ::active")
          ;(set-submode! ::active)
          ::active
          (println "would set submode ::detailed")
          ;(set-submode! ::detailed)
          nil)))))
(add-watch last-dist-stable :update-submode on-dist-change)
(comment
  (remove-watch last-dist-stable :update-submode))

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
(def pressed-before (atom false))
;(def motor (anim/animator))
(defn draw []
  (if (q/key-pressed?)
    (when (not= (q/key-as-keyword) (@state :last-key-pressed))
      (swap! state assoc :last-key-pressed (q/key-as-keyword))
      (case (q/key-as-keyword)
        :a (set-mode! ::auto)
        :m (set-mode! ::manual)
        :1 (set-submode! ::inactive)
        :2 (set-submode! ::active)
        :3 (set-submode! ::detailed)
        nil)))
  (let [invert false]
    (q/background (if invert 0 255))
    (q/fill (if invert 255 0)))
  (let [m1 (q/round (q/map-range (q/mouse-x) 0 (q/width) 0 180))
        m2 (q/round (q/map-range (q/mouse-y) 0 (q/height) 0 180))]
    (q/text-align :left :top)
    (q/text (str @mode " " @submode " "
                 (:vib-delay-based-on @auto))
            50 50)
    (if (manual-mode?)
      (do
        (comment
          (if (q/mouse-pressed?)
            (apply hap/set-motors
                   (case @submode
                     ::inactive [9 5]
                     ::active [42 19]
                     ::detailed [55 61]))))

        (comment
          (if (and (q/mouse-pressed?) (not @pressed-before))
            (reset! pressed-before true)

            (anim/set-state motor
                            #(hap/set-motors
                              (rlerp 9 5 %)
                              (rlerp 55 61 %))
                            200)))
        (hap/set-vibrating! (q/mouse-pressed?))
        (hap/set-motors m1 m2)
        )
      (do
        (reset! vib-delay-factor-atom (q/round (q/map-range (q/mouse-x) 0 (q/width) 1 1000))))))
  (let [i (atom -1)]
    (draw-bar (swap! i inc) "touch" @last-touch 0 255)
    (draw-bar (swap! i inc) "touch stable" @last-touch-stable 0 255)
    (draw-bar (swap! i inc) "distance" @last-dist 0 127)
    (draw-bar (swap! i inc) "distance stable" @last-dist-stable 0 127)
    (draw-bar (swap! i inc) "motor 2" (@hap/last-motor-status 1) 0 180)
    (draw-bar (swap! i inc) "motor 1" (@hap/last-motor-status 0) 0 180)
    (draw-bar (swap! i inc) "vibrating" (if @hap/vibrating 1 0) 0 1)
    (draw-bar (swap! i inc) "delay" @vib-delay-factor-atom 1 1000)))
(q/defsketch testsketch
             :title "motor test"
             :setup setup
             :draw draw
             :size [600 400])