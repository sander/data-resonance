(ns wifi.debug
  (:require [quil.core :as q]
            [wifi.haptic :as hap]
            [wifi.interaction :as ix]
            [wifi.sniffer :as sn]
            [clojure.core.async :as async]))

(def mode (atom ::manual))

(def interval 1000)
(def vib-delay-factor 100)
(def vib-duration 60)

(def manual (atom {:touch nil
                   :dist nil
                   :last-touch nil
                   :last-dist nil}))
(def auto (atom {:sniff nil
                 :vib-delay interval
                 :vib-delay-based-on 0}))

(defn start-manual-mode!
  []
  (let [[touch dist :as bla] (hap/listen2)]
    (swap! manual assoc
           :touch (ix/semidebounce touch 100)
           :dist dist)
    (async/go (while (= @mode ::manual)
                (swap! manual assoc :last-touch (async/<! (:touch @manual)))))
    (async/go (while (= @mode ::manual)
                (swap! manual assoc :last-dist (async/<! (:dist @manual)))))))

(defn end-manual-mode!
  []
  (hap/unlisten))

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
                                                       (/ interval (double @ival-count))))))
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
    (ix/ix)
    (swap! auto assoc :sniff sniff)))
(defn start-auto-mode! [] (ix/ix))

(defn end-auto-mode!
  []
  (async/unsub-all (:pub (@auto :sniff)))
  (sn/stop (@auto :sniff))
  (@ix/ix-instance))
(defn end-auto-mode! [] (@ix/ix-instance))

(start-manual-mode!)

(defn set-mode!
  [new-mode]
  (when (not= @mode new-mode)
    (reset! mode new-mode)
    (if (= new-mode ::manual)
      (do
        (end-auto-mode!)
        (start-manual-mode!)
        (println "running in manual mode"))
      (do
        (end-manual-mode!)
        (start-auto-mode!)
        (println "running in auto mode")))))

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
  (hap/set-vibrating! (q/mouse-pressed?))
  (let [m1 (q/round (q/map-range (q/mouse-x) 0 (q/width) 0 180))
        m2 (q/round (q/map-range (q/mouse-y) 0 (q/height) 0 180))]
    (q/text (str m1 ":" m2 " "
                 @mode
                 " (" @hap/last-motor-status ") ["
                 (:vib-delay-based-on @auto) "] {"
                 (:last-touch @manual) " - "
                 (:last-dist @manual) "}") 50 50)
    (if (= @mode ::manual)
      (do
        (hap/set-motors m1 m2))))
  (q/fill 0)
  (if @hap/vibrating
    (q/rect 100 100 20 20)))
(q/defsketch testsketch
             :title "motor test"
             :setup setup
             :draw draw
             :size [600 400])