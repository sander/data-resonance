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

(defonce vibration-intensity (atom 1))
(defonce vibration-skip (atom 1))
(def vibrating (atom false))
(def vibration-enabled (atom true))
(def vibration-up (atom false))
(def fps 60)
(def vibration-ms (/ 1000 fps))
(def vibration-active (atom false))

(defn should-vibrate?
  []
  (and @vibration-enabled @vibrating))

(defn vibrate
  []
  (when (not @vibration-active)
    (reset! vibration-active true)
    (go-loop
      []
      (when (should-vibrate?)
        (let [add ((if @vibration-up - +) @vibration-intensity)]
          (apply set-motors (map #(+ add %) @last-motor-status)))
        (swap! vibration-up not)
        (<! (timeout vibration-ms)))
      (if @vibrating
        (recur)
        (reset! vibration-active false)))))

(defn disable-vibration!
  [dur]
  (reset! vibration-enabled false)
  (go
    (<! (timeout dur))
    (reset! vibration-enabled true)))

(defn set-vibrating!
  [v]
  (when (not= @vibrating v)
    (reset! vibrating v)
    (if v (vibrate))))