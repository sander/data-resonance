(ns wifi.haptic
  (:require [serial-port :as serial]
            ;[quil.core :as q]
            [clojure.core.async :as async :refer [chan alts! go go-loop timeout <! >! put!]]))

(defn open
  "Opens the connection to Arduino."
  ([path] (serial/open path))
  ([] (open "/dev/tty.usbmodem1421")))

(defonce port (atom (open)))

(defn close
  "Closes the connection to Arduino."
  []
  (serial/close @port))

(defn reopen
  "Creates a new connection to Arduino."
  []
  (swap! port open))

(def last-motor-status (atom [0 0]))

(defn set-motors
  "Sets the motor positions to m1 and m2."
  [m1 m2]
  (reset! last-motor-status [m1 m2])
  (serial/write @port (byte-array [255 m1 m2])))

(defn manual
  "Switches to manual control mode using potmeters."
  []
  (reset! last-motor-status [0 0])
  (serial/write @port (byte-array [254])))

(defn listen
  "Listens to touch events from Arduino and puts :press or :release on the return channel."
  []
  (let [ch (chan)
        status (atom 0)]
    (serial/on-byte @port (fn [st]
                            (when (not= @status st)
                              (case st
                                1 (put! ch :release)
                                2 (put! ch :press)
                                nil))
                            (reset! status st)))
    ch))

(defn unlisten
  "Stops listening to touch events."
  []
  (serial/remove-listener @port))

(comment
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
                :size [600 400])))
