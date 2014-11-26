(ns wifi.ix2
  (:require [quil.core :as q]
            [wifi.haptic2 :as haptic]
            [wifi.animation :as anim]
            [wifi.filter :refer [stabilize below-threshold?]]
            [clojure.core.async :as async]))

(def state (atom {}))
(comment
  (def state (atom {:last {:servo1   -1
                           :servo2   -1
                           :pressure -1}}))

  (doseq [k (keys (:last @state))]
    (async/go
      (while true
        (let [val (async/<! (k listener))]
          (swap! state update-in [:last k] (fn [_] val)))))))

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

(defn new-key-pressed? [] (and (q/key-pressed?) (not= (q/key-as-keyword) (@state :last-key-pressed))))
(defn update-pressed-key [] (swap! state assoc :last-key-pressed (q/key-as-keyword)))

(defn setup [] (q/frame-rate 60))
(defn draw []
  (when (new-key-pressed?)
    (case (q/key-as-keyword)
      ;:a (set-mode! ::auto)
      ;:m (set-mode! ::manual)
      ;:1 (set-submode! ::inactive)
      ;:2 (set-submode! ::active)
      ;:3 (set-submode! ::detailed)
      nil))
  (q/background 255)
  (q/fill 0)
  (q/text-align :left :top)
  (q/text (str (@state :mode) " " (@state :submode)) 50 50)
  (haptic/set-vibrating! (q/key-pressed?))
  (let [i (atom -1)]
    (draw-bar (swap! i inc) "pressure" (@haptic/values :pressure) 0 255)
    (draw-bar (swap! i inc) "motor 2" (@haptic/values :servo2) 0 180)
    (draw-bar (swap! i inc) "motor 1" (@haptic/values :servo1) 0 180)
    ;(draw-bar (swap! i inc) "vibrating" (if @haptic/vibrating 1 0) 0 1)
    ))
(q/defsketch testsketch
             :title "motor test"
             :setup setup
             :draw draw
             :size [600 400])