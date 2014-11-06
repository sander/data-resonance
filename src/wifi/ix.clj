(ns wifi.ix
  (:require [quil.core :as q]
            [wifi.haptic :as haptic]
            [wifi.animation :as anim]
            [wifi.filter :refer [stabilize below-threshold?]]
            [clojure.core.async :as async]))

(def listener (haptic/listen3))

(def config (atom {:dist-change-timeout 1000}))

(def state (atom {:mode ::manual

                  :submode ::inactive
                  :submode-changed 0

                  :last-key-pressed nil

                  :touch (listener 0)
                  :dist (listener 1)}))

(def last-touch (atom 0))
(def last-dist (atom 0))

(def last-touch-stable (atom 0))
(def last-dist-stable (atom 0))

(stabilize last-touch last-touch-stable (@state :touch))
(stabilize last-dist last-dist-stable (@state :dist))

(defn auto-mode? [] (= (@state :mode) ::auto))
(defn manual-mode? [] (= (@state :mode) ::manual))

(defn wait! [ms] (async/<! (async/timeout ms)))

(def motor (anim/animator))
(def move-speed 200)
(defn move [p1 p2]
  (let [[s1 s2] @haptic/last-motor-status]
    (anim/set-state motor #(haptic/set-motors
                            (anim/rlerp s1 p1 %)
                            (anim/rlerp s2 p2 %)) move-speed)))

(defn set-submode! [new]
  (when (not= (@state :submode) new)
    (swap! state assoc :submode new)
    (println "setting submode" new)
    (apply move (case new
                  ::inactive [9 5]
                  ::active [42 19]
                  ::detailed [55 61]))
    (swap! state assoc :submode-changed (anim/millis))))

(set-submode! ::inactive)

(defn ready-to-accept-change? []
  (not (below-threshold?
         (anim/millis)
         (@state :submode-changed)
         (@config :dist-change-timeout))))
(defn direction [old new] (if (> new old) :up :down))

(defn on-dist-change
  [_ _ old new]
  (if (ready-to-accept-change?)
    (let [dir (direction old new)]
      (if (= dir :down)
        (case (@state :submode)
          ::inactive
          (set-submode! ::active)

          ::active
          (set-submode! ::detailed)

          ::detailed
          nil)
        (case (@state :submode)
          ::inactive
          nil

          ::active
          (println "would set submode ::inactive")

          ::detailed
          (println "would set submode ::active"))))))
(add-watch last-dist-stable :update-submode on-dist-change)

(defn refresh-watch []
  (remove-watch last-dist-stable :update-submode)
  (add-watch last-dist-stable :update-submode on-dist-change))

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

(defn setup [] (q/frame-rate 30))
(defn draw []
  (when (new-key-pressed?)
    (case (q/key-as-keyword)
      ;:a (set-mode! ::auto)
      ;:m (set-mode! ::manual)
      :1 (set-submode! ::inactive)
      :2 (set-submode! ::active)
      :3 (set-submode! ::detailed)
      nil))
  (q/background 255)
  (q/fill 0)
  (q/text-align :left :top)
  (q/text (str (@state :mode) " " (@state :submode)) 50 50)
  (let [i (atom -1)]
    (draw-bar (swap! i inc) "touch" @last-touch 0 255)
    (draw-bar (swap! i inc) "touch stable" @last-touch-stable 0 255)
    (draw-bar (swap! i inc) "distance" @last-dist 0 127)
    (draw-bar (swap! i inc) "distance stable" @last-dist-stable 0 127)
    (draw-bar (swap! i inc) "motor 2" (@haptic/last-motor-status 1) 0 180)
    (draw-bar (swap! i inc) "motor 1" (@haptic/last-motor-status 0) 0 180)
    (draw-bar (swap! i inc) "vibrating" (if @haptic/vibrating 1 0) 0 1)))
(q/defsketch testsketch
             :title "motor test"
             :setup setup
             :draw draw
             :size [600 400])